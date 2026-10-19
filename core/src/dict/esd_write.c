#include "dict/esd_format.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ds/strbuf.h"
#include "ds/vec.h"
#include "util/file_io.h"

typedef struct {
    char *word;
    char *phonetic;
    char *pos;
    char *translation;
} rec;

static char *dup_sub(const char *s, size_t len) {
    char *p = (char *)malloc(len + 1);
    if (p == NULL) {
        return NULL;
    }
    memcpy(p, s, len);
    p[len] = '\0';
    return p;
}

static void trim(const char *s, size_t len, const char **out, size_t *out_len) {
    size_t b = 0;
    size_t e = len;
    while (b < e && (s[b] == ' ' || s[b] == '\t')) {
        b++;
    }
    while (e > b && (s[e - 1] == ' ' || s[e - 1] == '\t' || s[e - 1] == '\r')) {
        e--;
    }
    *out = s + b;
    *out_len = e - b;
}

/* 解析一行：word,phonetic,pos,translation。
 * 前 3 个字段按逗号切分；释义（第 4 段）可能被双引号包裹、内含逗号与 "" 转义。 */
static int parse_line(const char *line, size_t len, rec *out) {
    size_t c1 = (size_t)-1, c2 = (size_t)-1, c3 = (size_t)-1;
    for (size_t i = 0; i < len; ++i) {
        if (line[i] == ',') {
            if (c1 == (size_t)-1) {
                c1 = i;
            } else if (c2 == (size_t)-1) {
                c2 = i;
            } else {
                c3 = i;
                break;
            }
        }
    }
    if (c1 == (size_t)-1 || c2 == (size_t)-1 || c3 == (size_t)-1) {
        return 0;
    }

    const char *w, *ph, *po, *tr;
    size_t wl, phl, pol, trl;
    trim(line, c1, &w, &wl);
    trim(line + c1 + 1, c2 - c1 - 1, &ph, &phl);
    trim(line + c2 + 1, c3 - c2 - 1, &po, &pol);
    trim(line + c3 + 1, len - c3 - 1, &tr, &trl);

    if (wl == 0) {
        return 0;
    }

    char *trans;
    if (trl >= 2 && tr[0] == '"' && tr[trl - 1] == '"') {
        es_strbuf sb;
        es_strbuf_init(&sb);
        for (size_t i = 1; i + 1 < trl; ++i) {
            if (tr[i] == '"' && i + 1 < trl - 1 && tr[i + 1] == '"') {
                es_strbuf_append(&sb, "\"", 1);
                i++;
            } else {
                es_strbuf_append(&sb, tr + i, 1);
            }
        }
        trans = es_strbuf_to_cstr(&sb);
        es_strbuf_destroy(&sb);
    } else {
        trans = dup_sub(tr, trl);
    }
    if (trans == NULL) {
        return 0;
    }

    out->word = dup_sub(w, wl);
    out->phonetic = dup_sub(ph, phl);
    out->pos = dup_sub(po, pol);
    out->translation = trans;
    if (out->word == NULL || out->phonetic == NULL || out->pos == NULL) {
        free(out->word);
        free(out->phonetic);
        free(out->pos);
        free(out->translation);
        return 0;
    }
    return 1;
}

static int is_header_word(const char *word) {
    return strcmp(word, "word") == 0 || strcmp(word, "Word") == 0 ||
           strcmp(word, "英文") == 0 || strcmp(word, "单词") == 0;
}

static void rec_free(rec *r) {
    free(r->word);
    free(r->phonetic);
    free(r->pos);
    free(r->translation);
}

static int rec_cmp(const void *a, const void *b) {
    const rec *ra = (const rec *)a;
    const rec *rb = (const rec *)b;
    return strcmp(ra->word, rb->word);
}

static int wr_u32(FILE *f, uint32_t v) {
    unsigned char b[4];
    b[0] = (unsigned char)(v & 0xFF);
    b[1] = (unsigned char)((v >> 8) & 0xFF);
    b[2] = (unsigned char)((v >> 16) & 0xFF);
    b[3] = (unsigned char)((v >> 24) & 0xFF);
    return fwrite(b, 1, 4, f) == 4;
}

es_status esd_build_from_csv(const char *csv_path, const char *esd_path) {
    if (csv_path == NULL || esd_path == NULL) {
        return ES_EINVAL;
    }

    unsigned char *raw = NULL;
    size_t raw_len = 0;
    es_status st = es_file_read_all(csv_path, &raw, &raw_len);
    if (st != ES_OK) {
        return st;
    }

    es_vec recs;
    es_vec_init(&recs, sizeof(rec));

    size_t start = 0;
    int header_checked = 0;
    for (size_t i = 0; i <= raw_len; ++i) {
        if (i == raw_len || raw[i] == '\n') {
            size_t len = i - start;
            const char *line = (const char *)raw + start;
            if (len > 0 && line[len - 1] == '\r') {
                len--;
            }
            rec r;
            if (parse_line(line, len, &r)) {
                if (!header_checked) {
                    header_checked = 1;
                    if (is_header_word(r.word)) {
                        rec_free(&r);
                        start = i + 1;
                        continue;
                    }
                }
                es_vec_push(&recs, &r);
            }
            start = i + 1;
        }
    }
    es_file_free(raw);

    uint32_t count = (uint32_t)recs.len;
    if (count > 0) {
        qsort(recs.data, recs.len, sizeof(rec), rec_cmp);
    }

    /* 构建 blob，并记录每个字段的偏移 */
    es_strbuf blob;
    es_strbuf_init(&blob);
    uint32_t *offs = NULL;
    if (count > 0) {
        offs = (uint32_t *)malloc((size_t)count * 4 * sizeof(uint32_t));
        if (offs == NULL) {
            es_vec_destroy(&recs);
            es_strbuf_destroy(&blob);
            return ES_ENOMEM;
        }
    }
    for (uint32_t i = 0; i < count; ++i) {
        rec *r = (rec *)es_vec_get(&recs, i);
        const char *fields[4] = {r->word, r->pos, r->phonetic, r->translation};
        for (int k = 0; k < 4; ++k) {
            offs[i * 4 + (uint32_t)k] = (uint32_t)es_strbuf_len(&blob);
            es_strbuf_append(&blob, fields[k], strlen(fields[k]) + 1);
        }
    }

    uint32_t index_off = ESD_HEADER_SIZE;
    uint32_t blob_off = index_off + count * ESD_INDEX_ENTRY_SIZE;
    uint32_t blob_len = (uint32_t)es_strbuf_len(&blob);

    FILE *f = fopen(esd_path, "wb");
    if (f == NULL) {
        free(offs);
        es_vec_destroy(&recs);
        es_strbuf_destroy(&blob);
        return ES_EIO;
    }

    int ok = 1;
    ok = ok && fwrite(ESD_MAGIC, 1, 4, f) == 4;
    ok = ok && wr_u32(f, ESD_VERSION);
    ok = ok && wr_u32(f, count);
    ok = ok && wr_u32(f, index_off);
    ok = ok && wr_u32(f, blob_off);
    ok = ok && wr_u32(f, blob_len);
    for (uint32_t i = 0; i < count && ok; ++i) {
        for (int k = 0; k < 4; ++k) {
            ok = ok && wr_u32(f, offs[i * 4 + (uint32_t)k]);
        }
    }
    if (ok && blob_len > 0) {
        ok = fwrite(blob.data, 1, blob_len, f) == blob_len;
    }
    if (fclose(f) != 0) {
        ok = 0;
    }

    for (uint32_t i = 0; i < count; ++i) {
        rec_free((rec *)es_vec_get(&recs, i));
    }
    free(offs);
    es_vec_destroy(&recs);
    es_strbuf_destroy(&blob);

    return ok ? ES_OK : ES_EIO;
}
