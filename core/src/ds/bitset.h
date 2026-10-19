#ifndef EASYLISTENING_DS_BITSET_H
#define EASYLISTENING_DS_BITSET_H

#include <stddef.h>
#include <stdint.h>
#include "easylistening/es_types.h"

/*
 * es_bitset：手写位图。
 * 用途：洗牌袋"已播放"标记、导入去重的布隆过滤器（设计文档 §5）。
 */
typedef struct es_bitset {
    uint64_t *words;
    size_t    nbits;
    size_t    nwords;
} es_bitset;

es_status es_bitset_init(es_bitset *b, size_t nbits);
void      es_bitset_destroy(es_bitset *b);

void      es_bitset_set_all(es_bitset *b, int value);
void      es_bitset_set(es_bitset *b, size_t i);
void      es_bitset_clear(es_bitset *b, size_t i);
int       es_bitset_get(const es_bitset *b, size_t i);

size_t    es_bitset_count(const es_bitset *b);
size_t    es_bitset_size(const es_bitset *b);

#endif /* EASYLISTENING_DS_BITSET_H */
