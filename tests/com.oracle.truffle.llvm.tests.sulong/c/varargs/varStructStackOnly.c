#include <stdarg.h>

struct _point {
    long x;
    long y;
    long z;
};

int foo(int x, ...)
{
    va_list argp;

    va_start(argp, x);

    struct _point res1 = va_arg(argp, struct _point);
    struct _point res2 = va_arg(argp, struct _point);

    va_end(argp);

    return res1.x + res1.y + res1.z + res2.x + res2.y + res2.z;
}

int main() {
    struct _point p = {19L, 13L, 9L};
    
    return foo(2, p, p);
}
