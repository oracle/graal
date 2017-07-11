#include <stdarg.h>

struct _point {
    long x;
    long y;
    long z;
};

int foo(int x, ...) {
    va_list argp;

    va_start(argp, x);

    struct _point res1 = va_arg(argp, struct _point);

    long l1 = va_arg(argp, long);
    long l2 = va_arg(argp, long);

    va_end(argp);

    return (int)(res1.x + res1.y) + l1 + l2;
}

int main() {
    struct _point p = {7L, 11L, 19L};

    return foo(1, p, 3L, 5L);
}
