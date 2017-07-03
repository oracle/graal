#include <stdarg.h>

struct _point {
    long x;
    long y;
};

int foo(long y0, long y1, long y2, ...) {
    va_list argp;

    va_start(argp, y2);

    int res = 0;

    res += va_arg(argp, struct _point).x;
    res += va_arg(argp, struct _point).x;
    res += va_arg(argp, struct _point).x;
    res += va_arg(argp, struct _point).x;
    res += va_arg(argp, struct _point).x;
    res += va_arg(argp, struct _point).x;

    va_end(argp);

    return res;
}

int main() {
    return foo(0L, 0L, 6L, 1L, 0L, 2L, 0L, 0L, 3L, 0L, 5L, 0L, 7L, 0L, 11L, 0L);
}
