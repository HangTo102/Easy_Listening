#include "ds/vec.h"

#include <stdlib.h>
#include <string.h>

es_status es_vec_init(es_vec *v, size_t elem_size) {
    if (v == NULL || elem_size == 0) {
        return ES_EINVAL;
    }
    v->data = NULL;
    v->len = 0;
    v->cap = 0;
    v->elem_size = elem_size;
    return ES_OK;
}

void es_vec_destroy(es_vec *v) {
    if (v == NULL) {
        return;
    }
    free(v->data);
    v->data = NULL;
    v->len = 0;
    v->cap = 0;
}

void es_vec_clear(es_vec *v) {
    if (v != NULL) {
        v->len = 0;
    }
}

es_status es_vec_reserve(es_vec *v, size_t new_cap) {
    if (v == NULL || v->elem_size == 0) {
        return ES_EINVAL;
    }
    if (new_cap <= v->cap) {
        return ES_OK;
    }

    size_t ncap = (v->cap == 0) ? 4 : v->cap;
    while (ncap < new_cap) {
        ncap *= 2;
    }

    void *p = realloc(v->data, ncap * v->elem_size);
    if (p == NULL) {
        return ES_ENOMEM;
    }
    v->data = (unsigned char *)p;
    v->cap = ncap;
    return ES_OK;
}

es_status es_vec_push(es_vec *v, const void *elem) {
    if (v == NULL || elem == NULL) {
        return ES_EINVAL;
    }
    if (v->len == v->cap) {
        es_status st = es_vec_reserve(v, v->len + 1);
        if (st != ES_OK) {
            return st;
        }
    }
    memcpy(v->data + v->len * v->elem_size, elem, v->elem_size);
    v->len++;
    return ES_OK;
}

void *es_vec_get(const es_vec *v, size_t index) {
    if (v == NULL || index >= v->len) {
        return NULL;
    }
    return v->data + index * v->elem_size;
}

size_t es_vec_len(const es_vec *v) {
    return (v == NULL) ? 0 : v->len;
}
