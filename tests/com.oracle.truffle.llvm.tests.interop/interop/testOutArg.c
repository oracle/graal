#include <truffle.h>

typedef struct test {
    int foo;
} test;

void* test_to_native(void* managed) {
    return truffle_handle_for_managed(managed);
}

int parse_arg(void* arg1, void* arg2, void* arg3) {
    *((int*)arg1) = 12;
    *((test**)arg2) = truffle_import_cached("global_object");
    *((char**)arg3) = "hello world";
}

int main(void) {
    int output1;
    test* output2;
    char* output3;

    parse_arg(&output1, &output2, &output3);

    if (output1 != 12) {
        return 1;
    }
    if (output2->foo != 1) {
        return 3;
    }
    if (strcmp(output3, "hello world")) {
        return 4;
    }

    return 0;
}
