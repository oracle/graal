#include <polyglot.h>

int main() {
    void *(*getstring)(int) = polyglot_import("getstring");
    char buffer[100];
    int l = polyglot_as_string(getstring(1), buffer, 100, "ascii");
    return l;
}
