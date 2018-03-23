#include <polyglot.h>

int test_fits_in(void *value) {
    int ret = 0;
    if (polyglot_fits_in_i8(value)) {
        ret |= 1;
    }
    if (polyglot_fits_in_i16(value)) {
        ret |= 2;
    }
    if (polyglot_fits_in_i32(value)) {
        ret |= 4;
    }
    if (polyglot_fits_in_i64(value)) {
        ret |= 8;
    }
    if (polyglot_fits_in_float(value)) {
        ret |= 16;
    }
    if (polyglot_fits_in_double(value)) {
        ret |= 32;
    }
    return ret;
}

int test_fits_in_nativeptr() {
    void *ptr = (void*) 0xdead;
    return test_fits_in(ptr);
}

