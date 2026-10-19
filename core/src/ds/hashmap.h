#ifndef EASYLISTENING_DS_HASHMAP_H
#define EASYLISTENING_DS_HASHMAP_H

#include <stddef.h>
#include "easylistening/es_types.h"

/*
 * es_hashmap：手写开放寻址哈希表（线性探测 + 墓碑删除）。
 * key 为 C 字符串（内部拷贝一份），value 为 void*。
 * 用途：导入去重、单词 -> id 映射（设计文档 §5 / §6.5）。
 */
typedef struct es_hashmap es_hashmap;

typedef void (*es_hashmap_visit)(const char *key, void *value, void *user);

es_hashmap *es_hashmap_create(void);
void        es_hashmap_destroy(es_hashmap *m);

size_t      es_hashmap_len(const es_hashmap *m);

/* 插入或覆盖。old_out 非空时回填被覆盖的旧值（新键时为 NULL）。 */
es_status   es_hashmap_put(es_hashmap *m, const char *key, void *value, void **old_out);
void       *es_hashmap_get(const es_hashmap *m, const char *key);
int         es_hashmap_contains(const es_hashmap *m, const char *key);

/* 删除。old_out 非空时回填被删除的值。 */
es_status   es_hashmap_remove(es_hashmap *m, const char *key, void **old_out);

void        es_hashmap_foreach(const es_hashmap *m, es_hashmap_visit fn, void *user);

#endif /* EASYLISTENING_DS_HASHMAP_H */
