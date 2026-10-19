#include "schedule/shuffle.h"

void es_shuffle_fisher_yates(int *a, size_t n, es_rng *rng) {
    if (n < 2) {
        return;
    }
    for (size_t i = n - 1; i > 0; --i) {
        size_t j = es_rng_below(rng, (uint32_t)(i + 1));
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}

void es_shuffle_bag_fill(int *out, size_t n, long last, es_rng *rng) {
    for (size_t i = 0; i < n; ++i) {
        out[i] = (int)i;
    }
    es_shuffle_fisher_yates(out, n, rng);

    if (n > 1 && (long)out[0] == last) {
        size_t j = 1 + es_rng_below(rng, (uint32_t)(n - 1));
        int t = out[0];
        out[0] = out[j];
        out[j] = t;
    }
}
