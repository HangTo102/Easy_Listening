#include "easylistening/es_types.h"

const char *es_status_str(es_status s) {
    switch (s) {
        case ES_OK:     return "ES_OK";
        case ES_ENOENT: return "ES_ENOENT";
        case ES_EINVAL: return "ES_EINVAL";
        case ES_EIO:    return "ES_EIO";
        case ES_ENOMEM: return "ES_ENOMEM";
        case ES_ESTATE: return "ES_ESTATE";
        default:        return "ES_UNKNOWN";
    }
}
