#ifndef EASYLISTENING_ES_TYPES_H
#define EASYLISTENING_ES_TYPES_H

#include <stddef.h>
#include <stdint.h>

/* 全内核统一的返回码。所有公共函数返回它，出参用指针。 */
typedef enum es_status {
    ES_OK = 0,   /* 成功 */
    ES_ENOENT,   /* 找不到（如单词不存在） */
    ES_EINVAL,   /* 参数非法 */
    ES_EIO,      /* 读写失败 */
    ES_ENOMEM,   /* 内存不足 */
    ES_ESTATE    /* 状态不对（如未初始化就调用） */
} es_status;

/* 把返回码转成人类可读字符串，方便打日志/测试。 */
const char *es_status_str(es_status s);

#endif /* EASYLISTENING_ES_TYPES_H */
