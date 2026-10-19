#ifndef EASYLISTENING_DS_VEC_H
#define EASYLISTENING_DS_VEC_H

#include <stddef.h>
#include "easylistening/es_types.h"

/*
 * es_vec：手写动态数组。
 * 按"元素大小"存放任意类型，追加摊还 O(1)，2 倍扩容。
 * 参考设计文档 §5 / §6.7。
 */
typedef struct es_vec {
    unsigned char *data;  /* 连续内存块 */
    size_t         len;   /* 已用元素个数 */
    size_t         cap;   /* 已分配元素个数 */
    size_t         elem_size; /* 单个元素字节数 */
} es_vec;

es_status es_vec_init(es_vec *v, size_t elem_size);
void      es_vec_destroy(es_vec *v);
void      es_vec_clear(es_vec *v);

es_status es_vec_reserve(es_vec *v, size_t new_cap);      /* 确保容量 >= new_cap */
es_status es_vec_push(es_vec *v, const void *elem);       /* 追加一个元素（拷贝） */
void     *es_vec_get(const es_vec *v, size_t index);      /* 取第 index 个元素；越界返回 NULL */
size_t    es_vec_len(const es_vec *v);

#endif /* EASYLISTENING_DS_VEC_H */
