#ifndef EASYLISTENING_ES_SCHEDULE_H
#define EASYLISTENING_ES_SCHEDULE_H

#include <stddef.h>
#include "easylistening/es_types.h"
#include "easylistening/es_vocab.h"

/*
 * 听力调度（设计文档 §6.3 / §6.4）。
 * MVP：洗牌袋随机 + 每日词数/全部 + 断点续播。
 * 词以"单词本内的下标"标识（与 es_vocab_word_get 的顺序一致）。
 */
typedef struct es_schedule es_schedule;

typedef struct es_schedule_cfg {
    int book_id;     /* 目标单词本；<0 表示未配置 */
    int daily_count; /* 每日播放词数；<=0 表示全部 */
} es_schedule_cfg;

/* vocab 由调用方持有，schedule 只引用不释放。path 为状态文件（可不存在）。 */
es_status es_schedule_open(es_schedule **out, es_vocab *vocab, const char *path);
void      es_schedule_close(es_schedule *s);

es_status es_schedule_configure(es_schedule *s, const es_schedule_cfg *cfg);
es_status es_schedule_config(const es_schedule *s, es_schedule_cfg *out);
es_status es_schedule_save(const es_schedule *s);

/* 开始今日会话：生成播放序列（洗牌袋）。 */
es_status es_schedule_start(es_schedule *s);
/* 下一个词的索引；会话结束返回 -1。 */
long      es_schedule_next(const es_schedule *s);
/* 提交当前词（played 暂未使用），前进并把断点落盘。 */
es_status es_schedule_commit(es_schedule *s, int played);

/* 回到上一个词：会话指针回退并落盘。返回该词下标；已在开头返回 -1。 */
long      es_schedule_prev(es_schedule *s);
/* 重播当前一轮：会话指针回到开头（顺序不变）。 */
es_status es_schedule_restart(es_schedule *s);

size_t    es_schedule_total(const es_schedule *s);
size_t    es_schedule_remaining(const es_schedule *s);

/* 把 n 个播放槽均匀分布到 [start_min, end_min) 分钟区间，写入 out[0..n-1]。 */
es_status es_schedule_slot_times(int start_min, int end_min, size_t n, int *out);

#endif /* EASYLISTENING_ES_SCHEDULE_H */
