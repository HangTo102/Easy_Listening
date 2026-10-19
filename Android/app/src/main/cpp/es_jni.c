#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <android/log.h>

#include "ds/strbuf.h"
#include "easylistening/es_dict.h"
#include "easylistening/es_schedule.h"
#include "easylistening/es_types.h"
#include "easylistening/es_vocab.h"
#include "util/file_io.h"

#define ES_LOG_TAG "EasyListeningCore"
#define ES_LOGI(...) __android_log_print(ANDROID_LOG_INFO, ES_LOG_TAG, __VA_ARGS__)

/* MVP 版全局会话：整个 App 共用一个 vocab + schedule + dict。 */
static es_vocab *g_vocab = NULL;
static es_schedule *g_schedule = NULL;
static es_dict *g_dict = NULL;
static int g_sched_book = -1;
static char g_dir[1024];

static char *dup_cstr(const char *s) {
    if (s == NULL) {
        return NULL;
    }
    size_t n = strlen(s);
    char *p = (char *)malloc(n + 1);
    if (p != NULL) {
        memcpy(p, s, n + 1);
    }
    return p;
}

static char *jstring_to_cstr(JNIEnv *env, jstring s) {
    if (s == NULL) {
        return NULL;
    }
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    if (c == NULL) {
        return NULL;
    }
    char *p = dup_cstr(c);
    (*env)->ReleaseStringUTFChars(env, s, c);
    return p;
}

static void close_all(void) {
    if (g_schedule != NULL) {
        es_schedule_close(g_schedule);
        g_schedule = NULL;
    }
    if (g_vocab != NULL) {
        es_vocab_save(g_vocab);
        es_vocab_close(g_vocab);
        g_vocab = NULL;
    }
    if (g_dict != NULL) {
        es_dict_close(g_dict);
        g_dict = NULL;
    }
    g_sched_book = -1;
}

static void save_vocab(void) {
    if (g_vocab != NULL) {
        es_vocab_save(g_vocab);
    }
}

/* 一行返回值的拼装：字段以 Tab 分隔。 */
static void append_word_line(es_strbuf *sb, const es_dict_entry *e) {
    es_strbuf_appendf(sb, "%s\t%s\t%s\t%s\n", e->word, e->phonetic, e->pos,
                      e->translation);
}

/* ---------------- 生命周期 ---------------- */

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeVersion(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, "0.1.0");
}

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeInit(JNIEnv *env, jclass clazz,
                                            jstring workdir) {
    (void)clazz;
    char *wd = jstring_to_cstr(env, workdir);
    if (wd == NULL) {
        return (*env)->NewStringUTF(env, "workdir 为空");
    }
    close_all();
    snprintf(g_dir, sizeof(g_dir), "%s", wd);
    free(wd);

    char vpath[1200];
    char spath[1200];
    snprintf(vpath, sizeof(vpath), "%s/vocab.db", g_dir);
    snprintf(spath, sizeof(spath), "%s/sched.db", g_dir);

    if (es_vocab_open(&g_vocab, vpath) != ES_OK) {
        g_vocab = NULL;
        return (*env)->NewStringUTF(env, "打开单词本失败");
    }
    if (es_schedule_open(&g_schedule, g_vocab, spath) != ES_OK) {
        es_vocab_close(g_vocab);
        g_vocab = NULL;
        g_schedule = NULL;
        return (*env)->NewStringUTF(env, "打开调度失败");
    }

    char dpath[1200];
    snprintf(dpath, sizeof(dpath), "%s/dict/main.esd", g_dir);
    es_dict_open(&g_dict, dpath); /* 词典可选，打开失败不影响其他功能 */

    return (*env)->NewStringUTF(env, "ok");
}

/* ---------------- 单词本 ---------------- */

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeListBooks(JNIEnv *env, jclass clazz) {
    (void)clazz;
    es_strbuf sb;
    es_strbuf_init(&sb);
    if (g_vocab != NULL) {
        size_t n = es_vocab_book_count(g_vocab);
        for (size_t i = 0; i < n; ++i) {
            es_book_info info;
            if (es_vocab_book_info(g_vocab, i, &info) == ES_OK) {
                es_strbuf_appendf(&sb, "%d\t%s\t%s\t%d\n", info.id, info.name,
                                  info.cover, info.word_count);
            }
        }
    }
    jstring ret = (*env)->NewStringUTF(env, es_strbuf_cstr(&sb));
    es_strbuf_destroy(&sb);
    return ret;
}

JNIEXPORT jlong JNICALL
Java_com_easylistening_app_NativeLib_nativeCreateBook(JNIEnv *env, jclass clazz,
                                                  jstring name) {
    (void)clazz;
    int id = -1;
    char *n = jstring_to_cstr(env, name);
    if (g_vocab != NULL && n != NULL) {
        es_vocab_book_create(g_vocab, n, "", &id);
        save_vocab();
    }
    free(n);
    return (jlong)id;
}

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeDeleteBook(JNIEnv *env, jclass clazz,
                                                  jlong id) {
    (void)clazz;
    (void)env;
    if (g_vocab != NULL) {
        es_vocab_book_remove(g_vocab, (int)id);
        save_vocab();
    }
}

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeRenameBook(JNIEnv *env, jclass clazz,
                                                  jlong id, jstring name) {
    (void)clazz;
    char *n = jstring_to_cstr(env, name);
    if (g_vocab != NULL && n != NULL) {
        es_vocab_book_rename(g_vocab, (int)id, n);
        save_vocab();
    }
    free(n);
}

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeSetCover(JNIEnv *env, jclass clazz, jlong id,
                                                jstring path) {
    (void)clazz;
    char *p = jstring_to_cstr(env, path);
    if (g_vocab != NULL && p != NULL) {
        es_vocab_book_set_cover(g_vocab, (int)id, p);
        save_vocab();
    }
    free(p);
}

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeMoveBook(JNIEnv *env, jclass clazz, jlong id,
                                                jint newIndex) {
    (void)clazz;
    (void)env;
    if (g_vocab != NULL) {
        es_vocab_book_move(g_vocab, (int)id, (size_t)newIndex);
        save_vocab();
    }
}

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeBookWords(JNIEnv *env, jclass clazz,
                                                 jlong id) {
    (void)clazz;
    es_strbuf sb;
    es_strbuf_init(&sb);
    if (g_vocab != NULL) {
        size_t n = es_vocab_word_count(g_vocab, (int)id);
        for (size_t i = 0; i < n; ++i) {
            es_dict_entry e;
            if (es_vocab_word_get(g_vocab, (int)id, i, &e) == ES_OK) {
                append_word_line(&sb, &e);
            }
        }
    }
    jstring ret = (*env)->NewStringUTF(env, es_strbuf_cstr(&sb));
    es_strbuf_destroy(&sb);
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_easylistening_app_NativeLib_nativeAddWord(JNIEnv *env, jclass clazz, jlong id,
                                               jstring word, jstring phonetic,
                                               jstring pos, jstring translation) {
    (void)clazz;
    if (g_vocab == NULL) {
        return -1;
    }
    es_dict_entry e;
    e.word = jstring_to_cstr(env, word);
    e.phonetic = jstring_to_cstr(env, phonetic);
    e.pos = jstring_to_cstr(env, pos);
    e.translation = jstring_to_cstr(env, translation);

    jint result = ES_OK;
    if (e.word == NULL || e.word[0] == '\0') {
        result = ES_EINVAL;
    } else {
        result = es_vocab_word_add(g_vocab, (int)id, &e);
        if (result == ES_OK) {
            save_vocab();
        }
    }

    free((void *)e.word);
    free((void *)e.phonetic);
    free((void *)e.pos);
    free((void *)e.translation);
    return result;
}

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeRemoveWord(JNIEnv *env, jclass clazz,
                                                  jlong id, jstring word) {
    (void)clazz;
    char *w = jstring_to_cstr(env, word);
    if (g_vocab != NULL && w != NULL) {
        es_vocab_word_remove(g_vocab, (int)id, w);
        save_vocab();
    }
    free(w);
}

/* 该词是否存在于任一单词本（只返回是否，不指明哪一个）。 */
JNIEXPORT jboolean JNICALL
Java_com_easylistening_app_NativeLib_nativeWordExistsAny(JNIEnv *env, jclass clazz,
                                                     jstring word) {
    (void)clazz;
    char *w = jstring_to_cstr(env, word);
    jboolean found = JNI_FALSE;
    if (g_vocab != NULL && w != NULL) {
        size_t n = es_vocab_book_count(g_vocab);
        for (size_t i = 0; i < n; ++i) {
            es_book_info info;
            if (es_vocab_book_info(g_vocab, i, &info) == ES_OK &&
                es_vocab_word_contains(g_vocab, info.id, w)) {
                found = JNI_TRUE;
                break;
            }
        }
    }
    free(w);
    return found;
}

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeImportTxt(JNIEnv *env, jclass clazz,
                                                 jlong id, jstring path) {
    (void)clazz;
    char *p = jstring_to_cstr(env, path);
    char buf[64];
    if (g_vocab == NULL || p == NULL) {
        free(p);
        return (*env)->NewStringUTF(env, "-1\t0");
    }
    int added = 0;
    int dup = 0;
    es_vocab_import_txt(g_vocab, (int)id, p, &added, &dup);
    save_vocab();
    free(p);
    snprintf(buf, sizeof(buf), "%d\t%d", added, dup);
    return (*env)->NewStringUTF(env, buf);
}

/*
 * 用打包进来的词表生成"内置单词本"。
 * 若已存在同名书则直接返回其 id；否则逐行读词、从词典补齐释义后建本。
 */
JNIEXPORT jlong JNICALL
Java_com_easylistening_app_NativeLib_nativeSeedBook(JNIEnv *env, jclass clazz,
                                                jstring name, jstring listPath) {
    (void)clazz;
    if (g_vocab == NULL) {
        return -1;
    }
    char *nm = jstring_to_cstr(env, name);
    char *path = jstring_to_cstr(env, listPath);
    if (nm == NULL || path == NULL) {
        free(nm);
        free(path);
        return -1;
    }

    size_t count = es_vocab_book_count(g_vocab);
    for (size_t i = 0; i < count; ++i) {
        es_book_info info;
        if (es_vocab_book_info(g_vocab, i, &info) == ES_OK && strcmp(info.name, nm) == 0) {
            jlong id = (jlong)info.id;
            free(nm);
            free(path);
            return id;
        }
    }

    int id = -1;
    es_vocab_book_create(g_vocab, nm, "", &id);

    unsigned char *raw = NULL;
    size_t raw_len = 0;
    if (es_file_read_all(path, &raw, &raw_len) == ES_OK) {
        size_t start = 0;
        for (size_t i = 0; i <= raw_len; ++i) {
            if (i == raw_len || raw[i] == '\n') {
                if (i < raw_len) {
                    raw[i] = '\0';
                }
                char *line = (char *)raw + start;
                size_t len = strlen(line);
                if (len > 0 && line[len - 1] == '\r') {
                    line[len - 1] = '\0';
                }
                while (*line == ' ' || *line == '\t') {
                    line++;
                }
                if (*line != '\0') {
                    es_dict_entry e;
                    if (g_dict != NULL && es_dict_lookup(g_dict, line, &e) == ES_OK) {
                        es_vocab_word_add(g_vocab, id, &e);
                    } else {
                        es_dict_entry m;
                        m.word = line;
                        m.phonetic = "";
                        m.pos = "";
                        m.translation = "";
                        es_vocab_word_add(g_vocab, id, &m);
                    }
                }
                start = i + 1;
            }
        }
        es_file_free(raw);
    }
    save_vocab();
    free(nm);
    free(path);
    return (jlong)id;
}

/* ---------------- 调度 ---------------- */

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleConfig(JNIEnv *env, jclass clazz,
                                                      jlong bookId, jint daily) {
    (void)clazz;
    (void)env;
    if (g_schedule == NULL) {
        return;
    }
    es_schedule_cfg cfg;
    cfg.book_id = (int)bookId;
    cfg.daily_count = (int)daily;
    es_schedule_configure(g_schedule, &cfg);
    g_sched_book = (int)bookId;
}

JNIEXPORT jint JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleStart(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    if (g_schedule == NULL) {
        return -1;
    }
    return (jint)es_schedule_start(g_schedule);
}

JNIEXPORT jlong JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleNext(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    if (g_schedule == NULL) {
        return -1;
    }
    return (jlong)es_schedule_next(g_schedule);
}

JNIEXPORT void JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleCommit(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    if (g_schedule != NULL) {
        es_schedule_commit(g_schedule, 1);
    }
}

JNIEXPORT jint JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleTotal(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    return (g_schedule == NULL) ? 0 : (jint)es_schedule_total(g_schedule);
}

JNIEXPORT jint JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleRemaining(JNIEnv *env,
                                                         jclass clazz) {
    (void)clazz;
    (void)env;
    return (g_schedule == NULL) ? 0 : (jint)es_schedule_remaining(g_schedule);
}

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleCurrentWord(JNIEnv *env,
                                                           jclass clazz) {
    (void)clazz;
    if (g_schedule == NULL || g_vocab == NULL) {
        return (*env)->NewStringUTF(env, "");
    }
    long idx = es_schedule_next(g_schedule);
    if (idx < 0) {
        return (*env)->NewStringUTF(env, "");
    }
    es_dict_entry e;
    if (es_vocab_word_get(g_vocab, g_sched_book, (size_t)idx, &e) != ES_OK) {
        return (*env)->NewStringUTF(env, "");
    }
    es_strbuf sb;
    es_strbuf_init(&sb);
    es_strbuf_appendf(&sb, "%s\t%s\t%s\t%s", e.word, e.phonetic, e.pos,
                      e.translation);
    const char *msg = es_strbuf_cstr(&sb);
    ES_LOGI("current word: %s", msg);
    jstring ret = (*env)->NewStringUTF(env, msg);
    es_strbuf_destroy(&sb);
    return ret;
}

JNIEXPORT jlong JNICALL
Java_com_easylistening_app_NativeLib_nativeSchedulePrev(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    if (g_schedule == NULL) {
        return -1;
    }
    return (jlong)es_schedule_prev(g_schedule);
}

JNIEXPORT jint JNICALL
Java_com_easylistening_app_NativeLib_nativeScheduleRestart(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    if (g_schedule == NULL) {
        return -1;
    }
    return (jint)es_schedule_restart(g_schedule);
}

/* ---------------- 词典 ---------------- */

JNIEXPORT jint JNICALL
Java_com_easylistening_app_NativeLib_nativeDictCount(JNIEnv *env, jclass clazz) {
    (void)clazz;
    (void)env;
    return (g_dict == NULL) ? 0 : (jint)es_dict_count(g_dict);
}

JNIEXPORT jstring JNICALL
Java_com_easylistening_app_NativeLib_nativeDictLookup(JNIEnv *env, jclass clazz,
                                                  jstring word) {
    (void)clazz;
    char *w = jstring_to_cstr(env, word);
    if (g_dict == NULL || w == NULL) {
        free(w);
        return (*env)->NewStringUTF(env, "");
    }
    es_dict_entry e;
    es_status st = es_dict_lookup(g_dict, w, &e);
    free(w);
    if (st != ES_OK) {
        return (*env)->NewStringUTF(env, "");
    }
    es_strbuf sb;
    es_strbuf_init(&sb);
    es_strbuf_appendf(&sb, "%s\t%s\t%s\t%s", e.word, e.phonetic, e.pos,
                      e.translation);
    const char *msg = es_strbuf_cstr(&sb);
    jstring ret = (*env)->NewStringUTF(env, msg);
    es_strbuf_destroy(&sb);
    return ret;
}
