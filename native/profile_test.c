#include "input_profile.h"
#include <assert.h>
#include <stdio.h>

int main(void) {
    assert(profile_for_model("TB321FU") == Y700_SINGLE);
    assert(profile_touch_count(profile_for_model("TB321FU")) == 1);
    assert(profile_touch_count(profile_for_model("SM-F9710")) == 2);
    assert(profile_for_model("unrecognized") == SAMSUNG_DUAL);
    assert(profile_is_touch(Y700_SINGLE, "NVTCapacitiveTouchScreen", 1, 1, 1, 1));
    assert(!profile_is_touch(Y700_SINGLE, "NVTCapacitivePen", 0, 0, 1, 1));
    assert(!profile_is_touch(Y700_SINGLE, "NVTCapacitiveTouchScreen", 1, 1, 1, 0));
    assert(!profile_is_touch(Y700_SINGLE, "NVTCapacitiveTouchScreen", 1, 0, 1, 1));
    assert(!profile_is_touch(Y700_SINGLE, "NVTCapacitiveTouchScreen", 1, 1, 0, 1));
    assert(!profile_is_touch(Y700_SINGLE, "sec_touchscreen", 1, 1, 1, 1));
    assert(profile_is_touch(SAMSUNG_DUAL, "sec_touchscreen", 1, 1, 1, 0));
    assert(profile_is_touch(SAMSUNG_DUAL, "sec_touchscreen2", 1, 1, 1, 0));
    assert(!profile_is_touch(SAMSUNG_DUAL, "NVTCapacitiveTouchScreen", 1, 1, 1, 1));
    puts("PASS: 13 profile/capability assertions; no input devices opened");
    return 0;
}
