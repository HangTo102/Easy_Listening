#ifndef EASYLISTENING_DS_HEAP_H
#define EASYLISTENING_DS_HEAP_H

#include <stddef.h>
#include "easylistening/es_types.h"

/*
 * es_heap：手写二叉最小堆（泛型，按比较器决定优先级）。
 * cmp(a,b) < 0 表示 a 优先级更高（先出堆）。
 * 用途：每日时段均匀分配、跨天均衡（设计文档 §5 / §6.4）。
 */
typedef int (*es_heap_cmp)(const void *a, const void *b);

typedef struct es_heap {
    unsigned char *data;
    size_t         len;
    size_t         cap;
    size_t         elem_size;
    es_heap_cmp    cmp;
} es_heap;

es_status es_heap_init(es_heap *h, size_t elem_size, es_heap_cmp cmp);
void      es_heap_destroy(es_heap *h);
void      es_heap_clear(es_heap *h);

es_status es_heap_push(es_heap *h, const void *elem);
int       es_heap_pop(es_heap *h, void *out); /* 出最小的一个；空堆返回 0 */
const void *es_heap_top(const es_heap *h);    /* 取最小；空堆返回 NULL */
size_t    es_heap_len(const es_heap *h);

#endif /* EASYLISTENING_DS_HEAP_H */
