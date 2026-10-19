#include <stdio.h>

#include "dict/esd_format.h"
#include "easylistening/es_types.h"

int main(int argc, char **argv) {
    if (argc != 3) {
        fprintf(stderr, "用法: esd_build <输入.csv> <输出.esd>\n");
        return 2;
    }
    es_status st = esd_build_from_csv(argv[1], argv[2]);
    if (st != ES_OK) {
        fprintf(stderr, "构建失败: %s\n", es_status_str(st));
        return 1;
    }
    printf("构建成功: %s\n", argv[2]);
    return 0;
}
