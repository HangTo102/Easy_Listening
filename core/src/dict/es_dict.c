#include "easylistening/es_dict.h"

#include <stdlib.h>
#include <string.h>

#include "dict/esd_format.h"
#include "util/file_io.h"

struct es_dict {
    unsigned char *buf;   /* 整个文件常驻内存 */
    size_t         size;
    uint32_t       count;
    uint32_t       index_off;
    uint32_t       blob_off;
};

static uint32_t rd_u32(const unsigned char *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) |
           ((uint32_t)p[3] << 24);
}

/* field: 0 word, 1 pos, 2 phonetic, 3 translation */
static const char *index_str(const es_dict *d, uint32_t i, int field) {
    size_t at = (size_t)d->index_off + (size_t)i * ESD_INDEX_ENTRY_SIZE +
                (size_t)field * 4;
    uint32_t off = rd_u32(d->buf + at);
    return (const char *)(d->buf + d->blob_off + off);
}

es_status es_dict_open(es_dict **out, const char *path) {
    if (out == NULL || path == NULL) {
        return ES_EINVAL;
    }

    unsigned char *buf = NULL;
    size_t size = 0;
    es_status st = es_file_read_all(path, &buf, &size);
    if (st != ES_OK) {
        return st;
    }

    if (size < ESD_HEADER_SIZE || memcmp(buf, ESD_MAGIC, 4) != 0) {
        es_file_free(buf);
        return ES_EIO;
    }
    uint32_t version = rd_u32(buf + 4);
    uint32_t count = rd_u32(buf + 8);
    uint32_t index_off = rd_u32(buf + 12);
    uint32_t blob_off = rd_u32(buf + 16);
    uint32_t blob_len = rd_u32(buf + 20);

    if (version != ESD_VERSION) {
        es_file_free(buf);
        return ES_EIO;
    }
    if ((size_t)index_off + (size_t)count * ESD_INDEX_ENTRY_SIZE > size ||
        (size_t)blob_off + (size_t)blob_len > size) {
        es_file_free(buf);
        return ES_EIO;
    }

    es_dict *d = (es_dict *)malloc(sizeof(es_dict));
    if (d == NULL) {
        es_file_free(buf);
        return ES_ENOMEM;
    }
    d->buf = buf;
    d->size = size;
    d->count = count;
    d->index_off = index_off;
    d->blob_off = blob_off;
    *out = d;
    return ES_OK;
}

void es_dict_close(es_dict *d) {
    if (d == NULL) {
        return;
    }
    es_file_free(d->buf);
    free(d);
}

size_t es_dict_count(const es_dict *d) {
    return (d == NULL) ? 0 : (size_t)d->count;
}

es_status es_dict_lookup(const es_dict *d, const char *word, es_dict_entry *out) {
    if (d == NULL || word == NULL || out == NULL) {
        return ES_EINVAL;
    }

    uint32_t lo = 0;
    uint32_t hi = d->count;
    while (lo < hi) {
        uint32_t mid = lo + (hi - lo) / 2;
        const char *mw = index_str(d, mid, 0);
        int cmp = strcmp(word, mw);
        if (cmp < 0) {
            hi = mid;
        } else if (cmp > 0) {
            lo = mid + 1;
        } else {
            out->word = mw;
            out->pos = index_str(d, mid, 1);
            out->phonetic = index_str(d, mid, 2);
            out->translation = index_str(d, mid, 3);
            return ES_OK;
        }
    }
    return ES_ENOENT;
}
