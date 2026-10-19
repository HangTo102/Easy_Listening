#ifndef EASYLISTENING_DS_STRBUF_H
#define EASYLISTENING_DS_STRBUF_H

#include <stddef.h>
#include "easylistening/es_types.h"

/*
 * es_strbuf：手写动态字符串缓冲区。
 * 始终以 '\0' 结尾，可当普通 C 字符串用；用于拼接 JSON、日志、导入导出。
 */
typedef struct es_strbuf {
    char  *data;
    size_t len;
    size_t cap;
} es_strbuf;

es_status   es_strbuf_init(es_strbuf *s);
void        es_strbuf_destroy(es_strbuf *s);
void        es_strbuf_clear(es_strbuf *s);

es_status   es_strbuf_append(es_strbuf *s, const char *bytes, size_t n);
es_status   es_strbuf_append_cstr(es_strbuf *s, const char *cstr);
es_status   es_strbuf_appendf(es_strbuf *s, const char *fmt, ...)
    __attribute__((format(printf, 2, 3)));

const char *es_strbuf_cstr(const es_strbuf *s);
char       *es_strbuf_to_cstr(const es_strbuf *s); /* 返回堆上副本，调用方 free/es_free */
size_t      es_strbuf_len(const es_strbuf *s);

#endif /* EASYLISTENING_DS_STRBUF_H */
