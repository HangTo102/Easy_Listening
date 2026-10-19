#include <stdio.h>

#include "easylistening/es_dict.h"
#include "easylistening/es_types.h"

/*
 * 桌面调试 CLI：查词。
 * 用法: es_cli <词典.esd> <单词> [单词...]
 */
int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "用法: es_cli <词典.esd> <单词> [单词...]\n");
        return 2;
    }

    es_dict *d = NULL;
    es_status st = es_dict_open(&d, argv[1]);
    if (st != ES_OK) {
        fprintf(stderr, "打开词典失败: %s\n", es_status_str(st));
        return 1;
    }

    printf("词典: %s (%u 词)\n", argv[1], (unsigned)es_dict_count(d));
    for (int i = 2; i < argc; ++i) {
        es_dict_entry e;
        st = es_dict_lookup(d, argv[i], &e);
        if (st == ES_OK) {
            printf("%s | 音标:%s | 词性:%s | %s\n", e.word, e.phonetic, e.pos,
                   e.translation);
        } else {
            printf("%s | <未收录>\n", argv[i]);
        }
    }

    es_dict_close(d);
    return 0;
}
