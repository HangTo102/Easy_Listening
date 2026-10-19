#ifndef EASYLISTENING_UTIL_RAND_H
#define EASYLISTENING_UTIL_RAND_H

#include <stdint.h>

/*
 * 小型可复现随机数发生器（splitmix64）。
 * 自带状态，便于测试与持久化（把 state 存下来即可续接）。
 */
typedef struct es_rng {
    uint64_t state;
} es_rng;

void     es_rng_seed(es_rng *r, uint64_t seed);
uint64_t es_rng_next(es_rng *r);
uint32_t es_rng_below(es_rng *r, uint32_t bound); /* [0, bound) */

#endif /* EASYLISTENING_UTIL_RAND_H */
