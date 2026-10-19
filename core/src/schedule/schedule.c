#include "easylistening/es_schedule.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "ds/strbuf.h"
#include "schedule/shuffle.h"
#include "util/file_io.h"
#include "util/rand.h"

/*
 * 状态文件格式（UTF-8 纯文本）：
 *   ESS1
 *   book\t<book_id>
 *   daily\t<daily_count>
 *   last\t<last_played_index>   (-1 表示无)
 *   rng\t<lo>\t<hi>             (64 位状态拆成两个 u32)
 */

struct es_schedule {
    es_vocab       *vocab; /* 不持有 */
    char           *path;
    es_schedule_cfg cfg;
    long            last_played;
    es_rng          rng;
    int            *session; /* 今日播放序列（词下标） */
    size_t          session_len;
    size_t          session_pos;
};

static char *dup_cstr(const char *s) {
    size_t n = strlen(s);
    char *p = (char *)malloc(n + 1);
    if (p != NULL) {
        memcpy(p, s, n + 1);
    }
    return p;
}

static void load_state(es_schedule *s) {
    if (!es_file_exists(s->path)) {
        return;
    }
    unsigned char *raw = NULL;
    size_t raw_len = 0;
    if (es_file_read_all(s->path, &raw, &raw_len) != ES_OK) {
        return;
    }

    uint32_t rng_lo = 0;
    uint32_t rng_hi = 0;
    int have_rng = 0;
    size_t start = 0;
    for (size_t i = 0; i <= raw_len; ++i) {
        if (i == raw_len || raw[i] == '\n') {
            if (i < raw_len) {
                raw[i] = '\0';
            }
            char *line = (char *)raw + start;
            size_t len = strlen(line);
            if (len > 0 && line[len - 1] == '\r') {
                line[len - 1] = '\0';
            }
            if (strncmp(line, "book\t", 5) == 0) {
                s->cfg.book_id = (int)strtol(line + 5, NULL, 10);
            } else if (strncmp(line, "daily\t", 6) == 0) {
                s->cfg.daily_count = (int)strtol(line + 6, NULL, 10);
            } else if (strncmp(line, "last\t", 5) == 0) {
                s->last_played = strtol(line + 5, NULL, 10);
            } else if (strncmp(line, "rng\t", 4) == 0) {
                char *p = line + 4;
                char *end = NULL;
                rng_lo = (uint32_t)strtoul(p, &end, 10);
                if (end != NULL && *end == '\t') {
                    rng_hi = (uint32_t)strtoul(end + 1, NULL, 10);
                }
                have_rng = 1;
            }
            start = i + 1;
        }
    }
    es_file_free(raw);
    if (have_rng) {
        s->rng.state = ((uint64_t)rng_hi << 32) | (uint64_t)rng_lo;
    }
}

es_status es_schedule_open(es_schedule **out, es_vocab *vocab, const char *path) {
    if (out == NULL || vocab == NULL || path == NULL) {
        return ES_EINVAL;
    }
    es_schedule *s = (es_schedule *)malloc(sizeof(es_schedule));
    if (s == NULL) {
        return ES_ENOMEM;
    }
    s->vocab = vocab;
    s->path = dup_cstr(path);
    if (s->path == NULL) {
        free(s);
        return ES_ENOMEM;
    }
    s->cfg.book_id = -1;
    s->cfg.daily_count = 0;
    s->last_played = -1;
    es_rng_seed(&s->rng, (uint64_t)time(NULL));
    s->session = NULL;
    s->session_len = 0;
    s->session_pos = 0;

    load_state(s);
    *out = s;
    return ES_OK;
}

void es_schedule_close(es_schedule *s) {
    if (s == NULL) {
        return;
    }
    free(s->session);
    free(s->path);
    free(s);
}

es_status es_schedule_configure(es_schedule *s, const es_schedule_cfg *cfg) {
    if (s == NULL || cfg == NULL) {
        return ES_EINVAL;
    }
    s->cfg = *cfg;
    return es_schedule_save(s);
}

es_status es_schedule_config(const es_schedule *s, es_schedule_cfg *out) {
    if (s == NULL || out == NULL) {
        return ES_EINVAL;
    }
    *out = s->cfg;
    return ES_OK;
}

es_status es_schedule_save(const es_schedule *s) {
    if (s == NULL) {
        return ES_EINVAL;
    }
    uint32_t lo = (uint32_t)(s->rng.state & 0xFFFFFFFFu);
    uint32_t hi = (uint32_t)(s->rng.state >> 32);
    es_strbuf sb;
    es_strbuf_init(&sb);
    es_strbuf_appendf(&sb, "ESS1\nbook\t%d\ndaily\t%d\nlast\t%ld\nrng\t%u\t%u\n",
                      s->cfg.book_id, s->cfg.daily_count, s->last_played,
                      (unsigned)lo, (unsigned)hi);
    es_status st = es_file_write_all(s->path, es_strbuf_cstr(&sb), es_strbuf_len(&sb));
    es_strbuf_destroy(&sb);
    return st;
}

es_status es_schedule_start(es_schedule *s) {
    if (s == NULL) {
        return ES_EINVAL;
    }
    if (s->cfg.book_id < 0) {
        return ES_ESTATE;
    }
    size_t n = es_vocab_word_count(s->vocab, s->cfg.book_id);
    if (n == 0) {
        return ES_ESTATE;
    }

    free(s->session);
    s->session = (int *)malloc(n * sizeof(int));
    if (s->session == NULL) {
        s->session_len = 0;
        s->session_pos = 0;
        return ES_ENOMEM;
    }

    es_shuffle_bag_fill(s->session, n, s->last_played, &s->rng);

    size_t len = n;
    if (s->cfg.daily_count > 0 && (size_t)s->cfg.daily_count < n) {
        len = (size_t)s->cfg.daily_count;
    }
    s->session_len = len;
    s->session_pos = 0;
    return ES_OK;
}

long es_schedule_next(const es_schedule *s) {
    if (s == NULL || s->session_pos >= s->session_len) {
        return -1;
    }
    return (long)s->session[s->session_pos];
}

es_status es_schedule_commit(es_schedule *s, int played) {
    (void)played; /* MVP：暂不区分 played/skipped */
    if (s == NULL) {
        return ES_EINVAL;
    }
    if (s->session_pos >= s->session_len) {
        return ES_ESTATE;
    }
    s->last_played = (long)s->session[s->session_pos];
    s->session_pos++;
    return es_schedule_save(s);
}

long es_schedule_prev(es_schedule *s) {
    if (s == NULL || s->session_pos == 0) {
        return -1;
    }
    s->session_pos--;
    s->last_played = (s->session_pos > 0) ? (long)s->session[s->session_pos - 1] : -1;
    es_schedule_save(s);
    return (long)s->session[s->session_pos];
}

es_status es_schedule_restart(es_schedule *s) {
    if (s == NULL) {
        return ES_EINVAL;
    }
    if (s->session == NULL || s->session_len == 0) {
        return ES_ESTATE;
    }
    s->session_pos = 0;
    s->last_played = -1;
    return es_schedule_save(s);
}

size_t es_schedule_total(const es_schedule *s) {
    return (s == NULL) ? 0 : s->session_len;
}

size_t es_schedule_remaining(const es_schedule *s) {
    if (s == NULL || s->session_pos > s->session_len) {
        return 0;
    }
    return s->session_len - s->session_pos;
}

es_status es_schedule_slot_times(int start_min, int end_min, size_t n, int *out) {
    if (n == 0) {
        return ES_OK;
    }
    if (out == NULL || end_min < start_min) {
        return ES_EINVAL;
    }
    long span = (long)(end_min - start_min);
    for (size_t i = 0; i < n; ++i) {
        out[i] = start_min + (int)(span * (long)i / (long)n);
    }
    return ES_OK;
}
