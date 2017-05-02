#include <truffle.h>

int callbackPointerArgTest(int (*callback)(struct test *), struct test *arg);

struct test {
    int x;
};

int callback(struct test *test) {
    return test->x;
}

int main() {
    struct test t;
    t.x = 42;
    return callbackPointerArgTest(callback, &t);
}
