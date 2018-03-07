#include <truffle.h>


typedef struct test {
    int foo;
} test;


test my_test_struct_global = { 1 };

int main(void) {
    truffle_assign_managed(&my_test_struct_global, truffle_import_cached("global_object"));

    if ((&my_test_struct_global)->foo != 1) {
        return 1;
    }

    return 0;
}
