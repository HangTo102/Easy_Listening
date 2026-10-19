#ifndef EASYLISTENING_UTIL_FILE_IO_H
#define EASYLISTENING_UTIL_FILE_IO_H

#include <stddef.h>
#include "easylistening/es_types.h"

/* 读取整个文件到堆内存。out_buf 需用 es_file_free 释放。 */
es_status es_file_read_all(const char *path, unsigned char **out_buf, size_t *out_size);

/* 写整个缓冲区到文件（覆盖）。 */
es_status es_file_write_all(const char *path, const void *buf, size_t size);

int       es_file_exists(const char *path);

void      es_file_free(void *buf);

#endif /* EASYLISTENING_UTIL_FILE_IO_H */
