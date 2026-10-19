#include "easylistening/es_vocab.h"

#include <stdlib.h>
#include <string.h>

#include "ds/strbuf.h"
#include "ds/vec.h"
#include "util/file_io.h"

/*
 * MVP 持久化格式（UTF-8 纯文本，字段以 Tab 分隔，特殊字符转义）：
 *
 *   ESV1
 *   B\t<id>\t<name>\t<cover>
 *   W\t<book_id>\t<word>\t<phonetic>\t<pos>\t<translation>
 *
 * 转义：反斜杠 -> \\   Tab -> \t   换行 -> \n   回车 -> \r
 */

typedef struct {
    char *word;
    char *phonetic;
    char *pos;
    char *translation;
} vword;

typedef struct {
    int    id;
    char  *name;
    char  *cover;
    es_vec words; /* of vword */
} vbook;

struct es_vocab {
    char  *path;
    es_vec books; /* of vbook */
    int    next_id;
};

/* ---------- 小工具 ---------- */

static char *dup_cstr(const char *s) {
    if (s == NULL) {
        s = "";
    }
    size_t n = strlen(s);
    char *p = (char *)malloc(n + 1);
    if (p != NULL) {
        memcpy(p, s, n + 1);
    }
    return p;
}

static es_status append_escaped(es_strbuf *sb, const char *s) {
    if (s == NULL) {
        s = "";
    }
    for (const char *p = s; *p != '\0'; ++p) {
        switch (*p) {
            case '\\': es_strbuf_append_cstr(sb, "\\\\"); break;
            case '\t': es_strbuf_append_cstr(sb, "\\t"); break;
            case '\n': es_strbuf_append_cstr(sb, "\\n"); break;
            case '\r': es_strbuf_append_cstr(sb, "\\r"); break;
            default:   es_strbuf_append(sb, p, 1); break;
        }
    }
    return ES_OK;
}

static char *unescape_dup(const char *s) {
    es_strbuf sb;
    es_strbuf_init(&sb);
    for (const char *p = s; *p != '\0'; ++p) {
        if (*p == '\\' && p[1] != '\0') {
            char c = p[1];
            if (c == 'n') {
                es_strbuf_append(&sb, "\n", 1);
            } else if (c == 't') {
                es_strbuf_append(&sb, "\t", 1);
            } else if (c == 'r') {
                es_strbuf_append(&sb, "\r", 1);
            } else if (c == '\\') {
                es_strbuf_append(&sb, "\\", 1);
            } else {
                es_strbuf_append(&sb, p, 2);
            }
            p++;
        } else {
            es_strbuf_append(&sb, p, 1);
        }
    }
    char *out = es_strbuf_to_cstr(&sb);
    es_strbuf_destroy(&sb);
    return out;
}

/* 把一行按 Tab 拆成若干 token（原地把 '\t' 换成 '\0'）。返回 token 数。 */
static int split_tabs(char *line, char **tokens, int max_tokens) {
    int n = 0;
    char *p = line;
    tokens[n++] = p;
    while (*p != '\0') {
        if (*p == '\t') {
            *p = '\0';
            if (n >= max_tokens) {
                break;
            }
            tokens[n++] = p + 1;
        }
        p++;
    }
    return n;
}

static void vword_free(vword *w) {
    free(w->word);
    free(w->phonetic);
    free(w->pos);
    free(w->translation);
}

static void vbook_destroy(vbook *b) {
    free(b->name);
    free(b->cover);
    for (size_t i = 0; i < b->words.len; ++i) {
        vword_free((vword *)es_vec_get(&b->words, i));
    }
    es_vec_destroy(&b->words);
}

static int find_book_index(const es_vocab *v, int id) {
    for (size_t i = 0; i < v->books.len; ++i) {
        vbook *b = (vbook *)es_vec_get(&v->books, i);
        if (b->id == id) {
            return (int)i;
        }
    }
    return -1;
}

static vbook *find_book(es_vocab *v, int id) {
    int idx = find_book_index(v, id);
    return (idx < 0) ? NULL : (vbook *)es_vec_get(&v->books, (size_t)idx);
}

static const vbook *find_book_const(const es_vocab *v, int id) {
    int idx = find_book_index(v, id);
    return (idx < 0) ? NULL : (const vbook *)es_vec_get(&v->books, (size_t)idx);
}

static int word_index_in_book(const vbook *b, const char *word) {
    for (size_t i = 0; i < b->words.len; ++i) {
        vword *w = (vword *)es_vec_get(&b->words, i);
        if (strcmp(w->word, word) == 0) {
            return (int)i;
        }
    }
    return -1;
}

/* ---------- 生命周期 ---------- */

es_status es_vocab_open(es_vocab **out, const char *path) {
    if (out == NULL || path == NULL) {
        return ES_EINVAL;
    }

    es_vocab *v = (es_vocab *)malloc(sizeof(es_vocab));
    if (v == NULL) {
        return ES_ENOMEM;
    }
    v->path = dup_cstr(path);
    es_vec_init(&v->books, sizeof(vbook));
    v->next_id = 1;

    if (v->path == NULL) {
        free(v);
        return ES_ENOMEM;
    }
    if (!es_file_exists(path)) {
        *out = v;
        return ES_OK;
    }

    unsigned char *raw = NULL;
    size_t raw_len = 0;
    es_status st = es_file_read_all(path, &raw, &raw_len);
    if (st != ES_OK) {
        es_vocab_close(v);
        return st;
    }

    size_t start = 0;
    int line_no = 0;
    for (size_t i = 0; i <= raw_len; ++i) {
        if (i == raw_len || raw[i] == '\n') {
            if (i < raw_len) {
                raw[i] = '\0';
            }
            size_t len = i - start;
            char *line = (char *)raw + start;
            if (len > 0 && line[len - 1] == '\r') {
                line[len - 1] = '\0';
                len--;
            }
            if (len > 0 && !(line_no == 0 && strcmp(line, "ESV1") == 0)) {
                char *tk[6];
                int nt = split_tabs(line, tk, 6);
                if (nt >= 1 && strcmp(tk[0], "B") == 0 && nt >= 4) {
                    vbook b;
                    b.id = atoi(tk[1]);
                    b.name = unescape_dup(tk[2]);
                    b.cover = unescape_dup(tk[3]);
                    es_vec_init(&b.words, sizeof(vword));
                    es_vec_push(&v->books, &b);
                    if (b.id >= v->next_id) {
                        v->next_id = b.id + 1;
                    }
                } else if (nt >= 1 && strcmp(tk[0], "W") == 0 && nt >= 6) {
                    vbook *b = find_book(v, atoi(tk[1]));
                    if (b != NULL) {
                        vword w;
                        w.word = unescape_dup(tk[2]);
                        w.phonetic = unescape_dup(tk[3]);
                        w.pos = unescape_dup(tk[4]);
                        w.translation = unescape_dup(tk[5]);
                        es_vec_push(&b->words, &w);
                    }
                }
            }
            line_no++;
            start = i + 1;
        }
    }
    es_file_free(raw);
    *out = v;
    return ES_OK;
}

es_status es_vocab_save(const es_vocab *v) {
    if (v == NULL) {
        return ES_EINVAL;
    }
    es_strbuf sb;
    es_strbuf_init(&sb);
    es_strbuf_append_cstr(&sb, "ESV1\n");
    for (size_t i = 0; i < v->books.len; ++i) {
        const vbook *b = (const vbook *)es_vec_get(&v->books, i);
        es_strbuf_appendf(&sb, "B\t%d\t", b->id);
        append_escaped(&sb, b->name);
        es_strbuf_append_cstr(&sb, "\t");
        append_escaped(&sb, b->cover);
        es_strbuf_append_cstr(&sb, "\n");
    }
    for (size_t i = 0; i < v->books.len; ++i) {
        const vbook *b = (const vbook *)es_vec_get(&v->books, i);
        for (size_t j = 0; j < b->words.len; ++j) {
            const vword *w = (const vword *)es_vec_get(&b->words, j);
            es_strbuf_appendf(&sb, "W\t%d\t", b->id);
            append_escaped(&sb, w->word);
            es_strbuf_append_cstr(&sb, "\t");
            append_escaped(&sb, w->phonetic);
            es_strbuf_append_cstr(&sb, "\t");
            append_escaped(&sb, w->pos);
            es_strbuf_append_cstr(&sb, "\t");
            append_escaped(&sb, w->translation);
            es_strbuf_append_cstr(&sb, "\n");
        }
    }
    es_status st = es_file_write_all(v->path, es_strbuf_cstr(&sb), es_strbuf_len(&sb));
    es_strbuf_destroy(&sb);
    return st;
}

void es_vocab_close(es_vocab *v) {
    if (v == NULL) {
        return;
    }
    for (size_t i = 0; i < v->books.len; ++i) {
        vbook_destroy((vbook *)es_vec_get(&v->books, i));
    }
    es_vec_destroy(&v->books);
    free(v->path);
    free(v);
}

/* ---------- 单词本 ---------- */

size_t es_vocab_book_count(const es_vocab *v) {
    return (v == NULL) ? 0 : v->books.len;
}

es_status es_vocab_book_create(es_vocab *v, const char *name, const char *cover,
                               int *out_id) {
    if (v == NULL || name == NULL) {
        return ES_EINVAL;
    }
    vbook b;
    b.id = v->next_id++;
    b.name = dup_cstr(name);
    b.cover = dup_cstr(cover);
    es_vec_init(&b.words, sizeof(vword));
    if (b.name == NULL || b.cover == NULL) {
        free(b.name);
        free(b.cover);
        es_vec_destroy(&b.words);
        return ES_ENOMEM;
    }
    es_status st = es_vec_push(&v->books, &b);
    if (st != ES_OK) {
        vbook_destroy(&b);
        return st;
    }
    if (out_id != NULL) {
        *out_id = b.id;
    }
    return ES_OK;
}

es_status es_vocab_book_rename(es_vocab *v, int id, const char *name) {
    if (v == NULL || name == NULL) {
        return ES_EINVAL;
    }
    vbook *b = find_book(v, id);
    if (b == NULL) {
        return ES_ENOENT;
    }
    char *n = dup_cstr(name);
    if (n == NULL) {
        return ES_ENOMEM;
    }
    free(b->name);
    b->name = n;
    return ES_OK;
}

es_status es_vocab_book_set_cover(es_vocab *v, int id, const char *cover) {
    if (v == NULL) {
        return ES_EINVAL;
    }
    vbook *b = find_book(v, id);
    if (b == NULL) {
        return ES_ENOENT;
    }
    char *c = dup_cstr(cover);
    if (c == NULL) {
        return ES_ENOMEM;
    }
    free(b->cover);
    b->cover = c;
    return ES_OK;
}

es_status es_vocab_book_remove(es_vocab *v, int id) {
    if (v == NULL) {
        return ES_EINVAL;
    }
    int idx = find_book_index(v, id);
    if (idx < 0) {
        return ES_ENOENT;
    }
    vbook *b = (vbook *)es_vec_get(&v->books, (size_t)idx);
    vbook_destroy(b);
    /* 用最后一个元素填补被删位置 */
    size_t last = v->books.len - 1;
    if ((size_t)idx != last) {
        memcpy(es_vec_get(&v->books, (size_t)idx), es_vec_get(&v->books, last),
               v->books.elem_size);
    }
    v->books.len--;
    return ES_OK;
}

es_status es_vocab_book_move(es_vocab *v, int id, size_t new_index) {
    if (v == NULL) {
        return ES_EINVAL;
    }
    int idx = find_book_index(v, id);
    if (idx < 0) {
        return ES_ENOENT;
    }
    if (new_index >= v->books.len) {
        new_index = v->books.len - 1;
    }
    if ((size_t)idx == new_index) {
        return ES_OK;
    }

    vbook tmp;
    memcpy(&tmp, es_vec_get(&v->books, (size_t)idx), v->books.elem_size);
    if ((size_t)idx < new_index) {
        for (size_t i = (size_t)idx; i < new_index; ++i) {
            memcpy(es_vec_get(&v->books, i), es_vec_get(&v->books, i + 1),
                   v->books.elem_size);
        }
    } else {
        for (size_t i = (size_t)idx; i > new_index; --i) {
            memcpy(es_vec_get(&v->books, i), es_vec_get(&v->books, i - 1),
                   v->books.elem_size);
        }
    }
    memcpy(es_vec_get(&v->books, new_index), &tmp, v->books.elem_size);
    return ES_OK;
}

es_status es_vocab_book_info(const es_vocab *v, size_t index, es_book_info *out) {
    if (v == NULL || out == NULL || index >= v->books.len) {
        return ES_EINVAL;
    }
    const vbook *b = (const vbook *)es_vec_get(&v->books, index);
    out->id = b->id;
    out->word_count = (int)b->words.len;
    out->name = b->name;
    out->cover = b->cover;
    return ES_OK;
}

/* ---------- 单词 ---------- */

size_t es_vocab_word_count(const es_vocab *v, int book_id) {
    const vbook *b = find_book_const(v, book_id);
    return (b == NULL) ? 0 : b->words.len;
}

int es_vocab_word_contains(const es_vocab *v, int book_id, const char *word) {
    const vbook *b = find_book_const(v, book_id);
    if (b == NULL || word == NULL) {
        return 0;
    }
    return word_index_in_book(b, word) >= 0 ? 1 : 0;
}

es_status es_vocab_word_add(es_vocab *v, int book_id, const es_dict_entry *e) {
    if (v == NULL || e == NULL || e->word == NULL) {
        return ES_EINVAL;
    }
    vbook *b = find_book(v, book_id);
    if (b == NULL) {
        return ES_ENOENT;
    }
    if (word_index_in_book(b, e->word) >= 0) {
        return ES_ESTATE; /* 已存在 */
    }
    vword w;
    w.word = dup_cstr(e->word);
    w.phonetic = dup_cstr(e->phonetic);
    w.pos = dup_cstr(e->pos);
    w.translation = dup_cstr(e->translation);
    if (w.word == NULL || w.phonetic == NULL || w.pos == NULL ||
        w.translation == NULL) {
        vword_free(&w);
        return ES_ENOMEM;
    }
    es_status st = es_vec_push(&b->words, &w);
    if (st != ES_OK) {
        vword_free(&w);
    }
    return st;
}

es_status es_vocab_word_remove(es_vocab *v, int book_id, const char *word) {
    if (v == NULL || word == NULL) {
        return ES_EINVAL;
    }
    vbook *b = find_book(v, book_id);
    if (b == NULL) {
        return ES_ENOENT;
    }
    int idx = word_index_in_book(b, word);
    if (idx < 0) {
        return ES_ENOENT;
    }
    vword_free((vword *)es_vec_get(&b->words, (size_t)idx));
    size_t last = b->words.len - 1;
    if ((size_t)idx != last) {
        memcpy(es_vec_get(&b->words, (size_t)idx), es_vec_get(&b->words, last),
               b->words.elem_size);
    }
    b->words.len--;
    return ES_OK;
}

es_status es_vocab_word_get(const es_vocab *v, int book_id, size_t index,
                            es_dict_entry *out) {
    if (v == NULL || out == NULL) {
        return ES_EINVAL;
    }
    const vbook *b = find_book_const(v, book_id);
    if (b == NULL || index >= b->words.len) {
        return ES_EINVAL;
    }
    const vword *w = (const vword *)es_vec_get(&b->words, index);
    out->word = w->word;
    out->phonetic = w->phonetic;
    out->pos = w->pos;
    out->translation = w->translation;
    return ES_OK;
}

/* ---------- 导入 / 导出 ---------- */

es_status es_vocab_import_txt(es_vocab *v, int book_id, const char *path,
                              int *out_added, int *out_dup) {
    if (v == NULL || path == NULL) {
        return ES_EINVAL;
    }
    if (find_book(v, book_id) == NULL) {
        return ES_ENOENT;
    }
    unsigned char *raw = NULL;
    size_t raw_len = 0;
    es_status st = es_file_read_all(path, &raw, &raw_len);
    if (st != ES_OK) {
        return st;
    }

    int added = 0;
    int dup = 0;
    size_t start = 0;
    for (size_t i = 0; i <= raw_len; ++i) {
        if (i == raw_len || raw[i] == '\n') {
            if (i < raw_len) {
                raw[i] = '\0';
            }
            size_t len = i - start;
            char *line = (char *)raw + start;
            while (len > 0 && (line[len - 1] == '\r' || line[len - 1] == ' ')) {
                line[--len] = '\0';
            }
            /* 去掉前导空白 */
            while (len > 0 && (*line == ' ' || *line == '\t')) {
                line++;
                len--;
            }
            if (len > 0) {
                es_dict_entry e;
                e.word = line;
                e.phonetic = "";
                e.pos = "";
                e.translation = "";
                st = es_vocab_word_add(v, book_id, &e);
                if (st == ES_OK) {
                    added++;
                } else if (st == ES_ESTATE) {
                    dup++;
                }
            }
            start = i + 1;
        }
    }
    es_file_free(raw);
    if (out_added != NULL) {
        *out_added = added;
    }
    if (out_dup != NULL) {
        *out_dup = dup;
    }
    return ES_OK;
}

es_status es_vocab_export_txt(const es_vocab *v, int book_id, const char *path) {
    if (v == NULL || path == NULL) {
        return ES_EINVAL;
    }
    const vbook *b = find_book_const(v, book_id);
    if (b == NULL) {
        return ES_ENOENT;
    }
    es_strbuf sb;
    es_strbuf_init(&sb);
    for (size_t i = 0; i < b->words.len; ++i) {
        const vword *w = (const vword *)es_vec_get(&b->words, i);
        es_strbuf_append_cstr(&sb, w->word);
        es_strbuf_append_cstr(&sb, "\n");
    }
    es_status st = es_file_write_all(path, es_strbuf_cstr(&sb), es_strbuf_len(&sb));
    es_strbuf_destroy(&sb);
    return st;
}
