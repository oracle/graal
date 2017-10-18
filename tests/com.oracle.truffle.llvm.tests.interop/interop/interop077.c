#include <truffle.h>
#include <stdio.h>

int main() {
    const char* cstr = truffle_string_to_cstr(truffle_execute(truffle_import("getstring"), 1));
    char buffer[100];
    int l = snprintf(buffer, 100, "%s", cstr);
    truffle_free_cstr(cstr);
    return l;
}
