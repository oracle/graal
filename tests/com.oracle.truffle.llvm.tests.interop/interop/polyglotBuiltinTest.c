#include <polyglot.h>

void *test_new(void *constructor) {
    return polyglot_new_instance(constructor, 42);
}

bool test_remove_member(void *object) {
    return polyglot_remove_member(object, "test");
}

bool test_remove_array_element(void *array) {
    return polyglot_remove_array_element(array, 3);
}

void *test_host_interop() {
    return polyglot_java_type("java.math.BigInteger");
}
