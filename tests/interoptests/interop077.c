#include <truffle.h>
#include <stdio.h>

int main() {
    const char* cstr = truffle_string_to_cstr(truffle_execute(truffle_import("getstring"), 1));
    int l = fprintf(stderr, "%s", cstr);
    truffle_free_cstr(cstr);
    return l;
}
