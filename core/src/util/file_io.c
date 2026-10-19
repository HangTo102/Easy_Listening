#include "util/file_io.h"

#include <stdio.h>
#include <stdlib.h>

es_status es_file_read_all(const char *path, unsigned char **out_buf, size_t *out_size) {
    if (path == NULL || out_buf == NULL || out_size == NULL) {
        return ES_EINVAL;
    }
    FILE *f = fopen(path, "rb");
    if (f == NULL) {
        return ES_EIO;
    }
    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        return ES_EIO;
    }
    long size = ftell(f);
    if (size < 0) {
        fclose(f);
        return ES_EIO;
    }
    if (fseek(f, 0, SEEK_SET) != 0) {
        fclose(f);
        return ES_EIO;
    }

    unsigned char *buf = (unsigned char *)malloc((size_t)size + 1);
    if (buf == NULL) {
        fclose(f);
        return ES_ENOMEM;
    }
    size_t got = fread(buf, 1, (size_t)size, f);
    fclose(f);
    if (got != (size_t)size) {
        free(buf);
        return ES_EIO;
    }
    buf[size] = '\0';
    *out_buf = buf;
    *out_size = (size_t)size;
    return ES_OK;
}

es_status es_file_write_all(const char *path, const void *buf, size_t size) {
    if (path == NULL || buf == NULL) {
        return ES_EINVAL;
    }
    FILE *f = fopen(path, "wb");
    if (f == NULL) {
        return ES_EIO;
    }
    size_t put = fwrite(buf, 1, size, f);
    if (fclose(f) != 0 || put != size) {
        return ES_EIO;
    }
    return ES_OK;
}

void es_file_free(void *buf) {
    free(buf);
}

int es_file_exists(const char *path) {
    if (path == NULL) {
        return 0;
    }
    FILE *f = fopen(path, "rb");
    if (f == NULL) {
        return 0;
    }
    fclose(f);
    return 1;
}
