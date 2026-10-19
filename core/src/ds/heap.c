#include "ds/heap.h"

#include <stdlib.h>
#include <string.h>

static unsigned char *elem_at(const es_heap *h, size_t i) {
    return h->data + i * h->elem_size;
}

static void swap_elems(es_heap *h, size_t a, size_t b) {
    unsigned char *pa = elem_at(h, a);
    unsigned char *pb = elem_at(h, b);
    for (size_t k = 0; k < h->elem_size; ++k) {
        unsigned char t = pa[k];
        pa[k] = pb[k];
        pb[k] = t;
    }
}

static void sift_up(es_heap *h, size_t i) {
    while (i > 0) {
        size_t parent = (i - 1) / 2;
        if (h->cmp(elem_at(h, i), elem_at(h, parent)) < 0) {
            swap_elems(h, i, parent);
            i = parent;
        } else {
            break;
        }
    }
}

static void sift_down(es_heap *h, size_t i) {
    for (;;) {
        size_t l = 2 * i + 1;
        size_t r = 2 * i + 2;
        size_t smallest = i;
        if (l < h->len && h->cmp(elem_at(h, l), elem_at(h, smallest)) < 0) {
            smallest = l;
        }
        if (r < h->len && h->cmp(elem_at(h, r), elem_at(h, smallest)) < 0) {
            smallest = r;
        }
        if (smallest == i) {
            break;
        }
        swap_elems(h, i, smallest);
        i = smallest;
    }
}

es_status es_heap_init(es_heap *h, size_t elem_size, es_heap_cmp cmp) {
    if (h == NULL || elem_size == 0 || cmp == NULL) {
        return ES_EINVAL;
    }
    h->data = NULL;
    h->len = 0;
    h->cap = 0;
    h->elem_size = elem_size;
    h->cmp = cmp;
    return ES_OK;
}

void es_heap_destroy(es_heap *h) {
    if (h == NULL) {
        return;
    }
    free(h->data);
    h->data = NULL;
    h->len = 0;
    h->cap = 0;
}

void es_heap_clear(es_heap *h) {
    if (h != NULL) {
        h->len = 0;
    }
}

es_status es_heap_push(es_heap *h, const void *elem) {
    if (h == NULL || elem == NULL) {
        return ES_EINVAL;
    }
    if (h->len == h->cap) {
        size_t ncap = (h->cap == 0) ? 8 : h->cap * 2;
        unsigned char *p = (unsigned char *)realloc(h->data, ncap * h->elem_size);
        if (p == NULL) {
            return ES_ENOMEM;
        }
        h->data = p;
        h->cap = ncap;
    }
    memcpy(elem_at(h, h->len), elem, h->elem_size);
    h->len++;
    sift_up(h, h->len - 1);
    return ES_OK;
}

int es_heap_pop(es_heap *h, void *out) {
    if (h == NULL || h->len == 0) {
        return 0;
    }
    if (out != NULL) {
        memcpy(out, h->data, h->elem_size);
    }
    h->len--;
    if (h->len > 0) {
        memcpy(h->data, elem_at(h, h->len), h->elem_size);
        sift_down(h, 0);
    }
    return 1;
}

const void *es_heap_top(const es_heap *h) {
    if (h == NULL || h->len == 0) {
        return NULL;
    }
    return h->data;
}

size_t es_heap_len(const es_heap *h) {
    return (h == NULL) ? 0 : h->len;
}
