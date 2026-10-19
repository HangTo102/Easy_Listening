#include "util/rand.h"

void es_rng_seed(es_rng *r, uint64_t seed) {
    r->state = seed;
}

uint64_t es_rng_next(es_rng *r) {
    uint64_t z = (r->state += 0x9E3779B97F4A7C15ULL);
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
    return z ^ (z >> 31);
}

uint32_t es_rng_below(es_rng *r, uint32_t bound) {
    if (bound == 0) {
        return 0;
    }
    return (uint32_t)(es_rng_next(r) % (uint64_t)bound);
}
