#ifndef UNLIT_INPUT_PROFILE_H
#define UNLIT_INPUT_PROFILE_H
#include <string.h>

enum input_profile { SAMSUNG_DUAL, Y700_SINGLE };
static inline enum input_profile profile_for_model(const char *model) {
    return !strcmp(model, "TB321FU") ? Y700_SINGLE : SAMSUNG_DUAL;
}
static inline int profile_touch_count(enum input_profile profile) {
    return profile == Y700_SINGLE ? 1 : 2;
}
static inline int profile_is_touch(enum input_profile profile, const char *name,
        int has_mt_x, int has_mt_y, int has_touch, int direct) {
    if (!has_mt_x || !has_mt_y || !has_touch) return 0;
    if (profile == Y700_SINGLE) return direct && !strcmp(name, "NVTCapacitiveTouchScreen");
    return !strcmp(name, "sec_touchscreen") || !strcmp(name, "sec_touchscreen2");
}
#endif
