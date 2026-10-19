#ifndef EASYLISTENING_ES_VOCAB_H
#define EASYLISTENING_ES_VOCAB_H

#include <stddef.h>
#include "easylistening/es_dict.h"
#include "easylistening/es_types.h"

/*
 * 单词本管理（设计文档 §8.2 / §9.3）。
 * 一个 es_vocab 是一整个"书架"：若干单词本，每个单词本含若干单词词条。
 *
 * MVP 持久化：单个纯文本文件（见 vocab.c 顶部格式说明），零第三方依赖。
 */
typedef struct es_vocab es_vocab;

typedef struct es_book_info {
    int         id;
    int         word_count;
    const char *name;  /* 指向内部存储 */
    const char *cover; /* 指向内部存储，可为空串 */
} es_book_info;

/* 打开（文件不存在则视为空书架）。修改后需调用 es_vocab_save 落盘。 */
es_status es_vocab_open(es_vocab **out, const char *path);
es_status es_vocab_save(const es_vocab *v);
void      es_vocab_close(es_vocab *v);

/* ---- 单词本 ---- */
size_t    es_vocab_book_count(const es_vocab *v);
es_status es_vocab_book_create(es_vocab *v, const char *name, const char *cover,
                               int *out_id);
es_status es_vocab_book_rename(es_vocab *v, int id, const char *name);
es_status es_vocab_book_set_cover(es_vocab *v, int id, const char *cover);
es_status es_vocab_book_remove(es_vocab *v, int id);
es_status es_vocab_book_move(es_vocab *v, int id, size_t new_index);
es_status es_vocab_book_info(const es_vocab *v, size_t index, es_book_info *out);

/* ---- 单词 ---- */
size_t    es_vocab_word_count(const es_vocab *v, int book_id);
int       es_vocab_word_contains(const es_vocab *v, int book_id, const char *word);
/* 新增词条（按 word 去重）。已存在返回 ES_ESTATE。 */
es_status es_vocab_word_add(es_vocab *v, int book_id, const es_dict_entry *e);
es_status es_vocab_word_remove(es_vocab *v, int book_id, const char *word);
es_status es_vocab_word_get(const es_vocab *v, int book_id, size_t index,
                            es_dict_entry *out);

/* ---- 导入 / 导出（.txt：每行一个单词）---- */
es_status es_vocab_import_txt(es_vocab *v, int book_id, const char *path,
                              int *out_added, int *out_dup);
es_status es_vocab_export_txt(const es_vocab *v, int book_id, const char *path);

#endif /* EASYLISTENING_ES_VOCAB_H */
