#ifndef EASYLISTENING_DICT_ESD_FORMAT_H
#define EASYLISTENING_DICT_ESD_FORMAT_H

#include <stdint.h>
#include "easylistening/es_types.h"

/*
 * .esd 文件格式（Easy Speak Dictionary），全部小端。
 *
 * [Header 24B]
 *   char  magic[4]   = "ESD1"
 *   u32   version    = 1
 *   u32   count      词条数
 *   u32   index_off  Index 区起始偏移
 *   u32   blob_off   Blob 区起始偏移
 *   u32   blob_len   Blob 区长度
 *
 * [Index]  count 个条目，每个 16B（偏移均相对 Blob 起点）：
 *   u32 word_off
 *   u32 pos_off
 *   u32 phonetic_off
 *   u32 translation_off
 *   条目按 word 的字节序升序排列（供二分查找）。
 *
 * [Blob] 所有字符串以 '\0' 结尾顺次存放。
 */

#define ESD_MAGIC            "ESD1"
#define ESD_VERSION          1u
#define ESD_HEADER_SIZE      24u
#define ESD_INDEX_ENTRY_SIZE 16u

/* 从 UTF-8 CSV（word,phonetic,pos,translation）构建 .esd。 */
es_status esd_build_from_csv(const char *csv_path, const char *esd_path);

#endif /* EASYLISTENING_DICT_ESD_FORMAT_H */
