#include <polyglot.h>

void *test_import_const() {
    return polyglot_import("constName");
}

void *test_import_var(const char *name) {
    return polyglot_import(name);
}

void test_export(const char *name) {
    void *str = polyglot_from_string("Hello, World!", "ascii");
    polyglot_export(name, str);
}
