#ifndef EASYLISTENING_ES_DICT_H
#define EASYLISTENING_ES_DICT_H

#include <stddef.h>
#include "easylistening/es_types.h"

/*
 * 只读词典引擎（设计文档 §6.1）。
 * 打开 .esd 文件后常驻内存，查词返回指向内部缓冲的字符串（无需释放）。
 */
typedef struct es_dict es_dict;

typedef struct es_dict_entry {
    const char *word;        /* 拼写 */
    const char *pos;         /* 词性，如 n./v. ，可能为空串 */
    const char *phonetic;    /* 音标，可能为空串 */
    const char *translation; /* 中文释义 */
} es_dict_entry;

/* 打开词典文件。成功后 *out 有效，需用 es_dict_close 释放。 */
es_status es_dict_open(es_dict **out, const char *path);
void      es_dict_close(es_dict *d);

size_t    es_dict_count(const es_dict *d);

/* 精确查词。命中返回 ES_OK 并填充 *out；找不到返回 ES_ENOENT。 */
es_status es_dict_lookup(const es_dict *d, const char *word, es_dict_entry *out);

#endif /* EASYLISTENING_ES_DICT_H */
