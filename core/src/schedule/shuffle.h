#ifndef EASYLISTENING_SCHED_SHUFFLE_H
#define EASYLISTENING_SCHED_SHUFFLE_H

#include <stddef.h>

#include "util/rand.h"

/*
 * 洗牌与"洗牌袋"（设计文档 §6.3）。
 */

/* Fisher-Yates 原地洗牌：保证每种排列等概率。 */
void es_shuffle_fisher_yates(int *a, size_t n, es_rng *rng);

/*
 * 洗牌袋：把 out[0..n-1] 填充为 0..n-1 的一个随机排列。
 * 若 last >= 0 且排列首元素恰好等于 last，则与随机位置交换，
 * 消除"跨轮接缝"处相邻重复（一圈内本就无重复）。
 */
void es_shuffle_bag_fill(int *out, size_t n, long last, es_rng *rng);

#endif /* EASYLISTENING_SCHED_SHUFFLE_H */
