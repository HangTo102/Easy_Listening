#include "ds/hashmap.h"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

enum { SLOT_EMPTY = 0, SLOT_USED = 1, SLOT_TOMB = 2 };

typedef struct slot {
    char   *key;
    void   *value;
    uint8_t state;
} slot;

struct es_hashmap {
    slot  *slots;
    size_t cap;   /* 恒为 2 的幂 */
    size_t used;  /* SLOT_USED 个数 */
    size_t tombs; /* SLOT_TOMB 个数 */
};

/* FNV-1a 64 位散列，简单且分布良好。 */
static uint64_t fnv1a(const char *s) {
    uint64_t h = 1469598103934665603ULL;
    const unsigned char *p = (const unsigned char *)s;
    for (; *p != '\0'; ++p) {
        h ^= (uint64_t)(*p);
        h *= 1099511628211ULL;
    }
    return h;
}

static char *dup_cstr(const char *s) {
    size_t n = strlen(s);
    char *p = (char *)malloc(n + 1);
    if (p != NULL) {
        memcpy(p, s, n + 1);
    }
    return p;
}

/* 定位 key 对应槽位：命中已存在键则返回它；否则返回首个可写槽（空或墓碑）。 */
static slot *find_slot(const es_hashmap *m, const char *key, uint64_t h) {
    size_t mask = m->cap - 1;
    size_t i = (size_t)h & mask;
    slot *first_tomb = NULL;
    for (;;) {
        slot *s = &((slot *)m->slots)[i];
        if (s->state == SLOT_EMPTY) {
            return (first_tomb != NULL) ? first_tomb : s;
        }
        if (s->state == SLOT_USED && strcmp(s->key, key) == 0) {
            return s;
        }
        if (s->state == SLOT_TOMB && first_tomb == NULL) {
            first_tomb = s;
        }
        i = (i + 1) & mask;
    }
}

static es_status hm_resize(es_hashmap *m, size_t new_cap) {
    slot *old = m->slots;
    size_t old_cap = m->cap;

    slot *ns = (slot *)calloc(new_cap, sizeof(slot));
    if (ns == NULL) {
        return ES_ENOMEM;
    }
    m->slots = ns;
    m->cap = new_cap;
    m->used = 0;
    m->tombs = 0;

    for (size_t i = 0; i < old_cap; ++i) {
        if (old[i].state == SLOT_USED) {
            uint64_t h = fnv1a(old[i].key);
            slot *dst = find_slot(m, old[i].key, h);
            dst->key = old[i].key; /* 转移所有权，不复制 */
            dst->value = old[i].value;
            dst->state = SLOT_USED;
            m->used++;
        }
    }
    free(old);
    return ES_OK;
}

es_hashmap *es_hashmap_create(void) {
    es_hashmap *m = (es_hashmap *)calloc(1, sizeof(es_hashmap));
    if (m == NULL) {
        return NULL;
    }
    m->cap = 16;
    m->slots = (slot *)calloc(m->cap, sizeof(slot));
    if (m->slots == NULL) {
        free(m);
        return NULL;
    }
    m->used = 0;
    m->tombs = 0;
    return m;
}

void es_hashmap_destroy(es_hashmap *m) {
    if (m == NULL) {
        return;
    }
    if (m->slots != NULL) {
        for (size_t i = 0; i < m->cap; ++i) {
            if (m->slots[i].state == SLOT_USED) {
                free(m->slots[i].key);
            }
        }
    }
    free(m->slots);
    free(m);
}

size_t es_hashmap_len(const es_hashmap *m) {
    return (m == NULL) ? 0 : m->used;
}

es_status es_hashmap_put(es_hashmap *m, const char *key, void *value, void **old_out) {
    if (m == NULL || key == NULL) {
        return ES_EINVAL;
    }
    /* 负载因子 0.7：used+tombs 逼近时扩容（顺带清理墓碑） */
    if ((m->used + m->tombs + 1) * 10 >= m->cap * 7) {
        es_status st = hm_resize(m, m->cap * 2);
        if (st != ES_OK) {
            return st;
        }
    }

    uint64_t h = fnv1a(key);
    slot *s = find_slot(m, key, h);

    if (s->state == SLOT_USED) {
        if (old_out != NULL) {
            *old_out = s->value;
        }
        s->value = value;
        return ES_OK;
    }

    char *k = dup_cstr(key);
    if (k == NULL) {
        return ES_ENOMEM;
    }
    if (s->state == SLOT_TOMB) {
        m->tombs--;
    }
    s->key = k;
    s->value = value;
    s->state = SLOT_USED;
    m->used++;
    if (old_out != NULL) {
        *old_out = NULL;
    }
    return ES_OK;
}

void *es_hashmap_get(const es_hashmap *m, const char *key) {
    if (m == NULL || key == NULL) {
        return NULL;
    }
    uint64_t h = fnv1a(key);
    slot *s = find_slot(m, key, h);
    if (s->state == SLOT_USED) {
        return s->value;
    }
    return NULL;
}

int es_hashmap_contains(const es_hashmap *m, const char *key) {
    if (m == NULL || key == NULL) {
        return 0;
    }
    uint64_t h = fnv1a(key);
    slot *s = find_slot(m, key, h);
    return (s->state == SLOT_USED) ? 1 : 0;
}

es_status es_hashmap_remove(es_hashmap *m, const char *key, void **old_out) {
    if (m == NULL || key == NULL) {
        return ES_EINVAL;
    }
    uint64_t h = fnv1a(key);
    slot *s = find_slot(m, key, h);
    if (s->state != SLOT_USED) {
        return ES_ENOENT;
    }
    if (old_out != NULL) {
        *old_out = s->value;
    }
    free(s->key);
    s->key = NULL;
    s->value = NULL;
    s->state = SLOT_TOMB;
    m->used--;
    m->tombs++;
    return ES_OK;
}

void es_hashmap_foreach(const es_hashmap *m, es_hashmap_visit fn, void *user) {
    if (m == NULL || fn == NULL) {
        return;
    }
    for (size_t i = 0; i < m->cap; ++i) {
        if (m->slots[i].state == SLOT_USED) {
            fn(m->slots[i].key, m->slots[i].value, user);
        }
    }
}
