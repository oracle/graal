#include <polyglot.h>

int check_types(void *value) {
    int ret = 0;
    if (polyglot_is_value(value)) {
        ret |= 1;
    }
    if (polyglot_is_null(value)) {
        ret |= 2;
    }
    if (polyglot_is_number(value)) {
        ret |= 4;
    }
    if (polyglot_is_boolean(value)) {
        ret |= 8;
    }
    if (polyglot_is_string(value)) {
        ret |= 16;
    }
    if (polyglot_can_execute(value)) {
        ret |= 32;
    }
    if (polyglot_has_array_elements(value)) {
        ret |= 64;
    }
    if (polyglot_has_members(value)) {
        ret |= 128;
    }
    if (polyglot_can_instantiate(value)) {
        ret |= 256;
    }
    return ret;
}

int check_types_nativeptr() {
    void *ptr = (void *) 0xdead;
    return check_types(ptr);
}
