#include "ds/bitset.h"

#include <stdlib.h>
#include <string.h>

es_status es_bitset_init(es_bitset *b, size_t nbits) {
    if (b == NULL || nbits == 0) {
        return ES_EINVAL;
    }
    size_t nwords = (nbits + 63) / 64;
    b->words = (uint64_t *)calloc(nwords, sizeof(uint64_t));
    if (b->words == NULL) {
        return ES_ENOMEM;
    }
    b->nbits = nbits;
    b->nwords = nwords;
    return ES_OK;
}

void es_bitset_destroy(es_bitset *b) {
    if (b == NULL) {
        return;
    }
    free(b->words);
    b->words = NULL;
    b->nbits = 0;
    b->nwords = 0;
}

void es_bitset_set_all(es_bitset *b, int value) {
    if (b == NULL || b->words == NULL) {
        return;
    }
    memset(b->words, value ? 0xFF : 0x00, b->nwords * sizeof(uint64_t));
    /* 末字超出的高位清掉，保证 count 正确 */
    size_t extra = b->nwords * 64 - b->nbits;
    if (extra > 0) {
        b->words[b->nwords - 1] >>= extra;
        b->words[b->nwords - 1] <<= extra;
    }
}

void es_bitset_set(es_bitset *b, size_t i) {
    if (b == NULL || i >= b->nbits) {
        return;
    }
    b->words[i >> 6] |= (uint64_t)1 << (i & 63);
}

void es_bitset_clear(es_bitset *b, size_t i) {
    if (b == NULL || i >= b->nbits) {
        return;
    }
    b->words[i >> 6] &= ~((uint64_t)1 << (i & 63));
}

int es_bitset_get(const es_bitset *b, size_t i) {
    if (b == NULL || i >= b->nbits) {
        return 0;
    }
    return (b->words[i >> 6] >> (i & 63)) & 1u ? 1 : 0;
}

size_t es_bitset_count(const es_bitset *b) {
    if (b == NULL) {
        return 0;
    }
    size_t total = 0;
    for (size_t i = 0; i < b->nwords; ++i) {
        total += (size_t)__builtin_popcountll(b->words[i]);
    }
    return total;
}

size_t es_bitset_size(const es_bitset *b) {
    return (b == NULL) ? 0 : b->nbits;
}
