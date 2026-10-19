#include "ds/strbuf.h"

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static es_status strbuf_reserve(es_strbuf *s, size_t need) {
    /* 需要能放下 need 个字符 + 结尾 '\0' */
    if (s->cap >= need + 1) {
        return ES_OK;
    }
    size_t ncap = (s->cap == 0) ? 16 : s->cap;
    while (ncap < need + 1) {
        ncap *= 2;
    }
    char *p = (char *)realloc(s->data, ncap);
    if (p == NULL) {
        return ES_ENOMEM;
    }
    s->data = p;
    s->cap = ncap;
    return ES_OK;
}

es_status es_strbuf_init(es_strbuf *s) {
    if (s == NULL) {
        return ES_EINVAL;
    }
    s->data = NULL;
    s->len = 0;
    s->cap = 0;
    return ES_OK;
}

void es_strbuf_destroy(es_strbuf *s) {
    if (s == NULL) {
        return;
    }
    free(s->data);
    s->data = NULL;
    s->len = 0;
    s->cap = 0;
}

void es_strbuf_clear(es_strbuf *s) {
    if (s == NULL) {
        return;
    }
    s->len = 0;
    if (s->data != NULL) {
        s->data[0] = '\0';
    }
}

es_status es_strbuf_append(es_strbuf *s, const char *bytes, size_t n) {
    if (s == NULL || (bytes == NULL && n != 0)) {
        return ES_EINVAL;
    }
    es_status st = strbuf_reserve(s, s->len + n);
    if (st != ES_OK) {
        return st;
    }
    if (n != 0) {
        memcpy(s->data + s->len, bytes, n);
    }
    s->len += n;
    s->data[s->len] = '\0';
    return ES_OK;
}

es_status es_strbuf_append_cstr(es_strbuf *s, const char *cstr) {
    if (cstr == NULL) {
        return ES_EINVAL;
    }
    return es_strbuf_append(s, cstr, strlen(cstr));
}

es_status es_strbuf_appendf(es_strbuf *s, const char *fmt, ...) {
    if (s == NULL || fmt == NULL) {
        return ES_EINVAL;
    }

    va_list ap;
    va_start(ap, fmt);
    va_list ap2;
    va_copy(ap2, ap);
    int n = vsnprintf(NULL, 0, fmt, ap);
    va_end(ap);

    if (n < 0) {
        va_end(ap2);
        return ES_EINVAL;
    }

    es_status st = strbuf_reserve(s, s->len + (size_t)n);
    if (st != ES_OK) {
        va_end(ap2);
        return st;
    }
    vsnprintf(s->data + s->len, (size_t)n + 1, fmt, ap2);
    va_end(ap2);

    s->len += (size_t)n;
    return ES_OK;
}

const char *es_strbuf_cstr(const es_strbuf *s) {
    if (s == NULL || s->data == NULL) {
        return "";
    }
    return s->data;
}

char *es_strbuf_to_cstr(const es_strbuf *s) {
    const char *src = es_strbuf_cstr(s);
    size_t n = strlen(src);
    char *p = (char *)malloc(n + 1);
    if (p == NULL) {
        return NULL;
    }
    memcpy(p, src, n + 1);
    return p;
}

size_t es_strbuf_len(const es_strbuf *s) {
    return (s == NULL) ? 0 : s->len;
}
