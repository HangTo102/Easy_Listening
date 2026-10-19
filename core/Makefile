# Easy_Listening C 内核构建脚本（桌面 / Windows-MinGW）
# 用法（在 core/ 目录下）：
#   mingw32-make          编译并运行全部单元测试
#   mingw32-make tools    编译 esd_build / es_cli 工具
# 说明：M0/M1 阶段用 Makefile；接入 Android 时再加 CMakeLists.txt（NDK 需要）。

CC     := gcc
CFLAGS := -std=c11 -Wall -Wextra -Werror -g -Iinclude -Isrc

SRC    := $(wildcard src/*/*.c)
TEST   := $(wildcard tests/*.c)

.PHONY: all test tools

all: tools

test:
	$(CC) $(CFLAGS) $(SRC) $(TEST) -o es_tests.exe
	es_tests.exe

tools:
	$(CC) $(CFLAGS) $(SRC) tools/esd_build.c -o esd_build.exe
	$(CC) $(CFLAGS) $(SRC) tools/es_cli.c -o es_cli.exe
