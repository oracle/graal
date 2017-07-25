#include <stdarg.h>

struct _point {
    double x;
    double y;
};

int foo(double y0, double y1, double y2, ...) {
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
    return foo(0., 0., 6., 1., 0., 2., 0., 0., 3., 0., 5., 0., 7., 0., 11., 0.);
}
