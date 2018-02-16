#include <truffle.h>


typedef struct test {
    int foo;
} test;


test my_test_struct_global;

int main(void) {
    test* my_test_struct_local = truffle_handle_for_managed(truffle_import("local_object"));

    if (my_test_struct_local->foo != 2) {
        return 2;
    }

    my_test_struct_local->foo = 10;
    if (my_test_struct_local->foo != 20) {
        return 4;
    }

    return 0;
}
