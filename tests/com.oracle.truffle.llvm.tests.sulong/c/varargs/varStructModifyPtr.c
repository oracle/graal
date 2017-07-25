#include <stdarg.h>

struct _bar {
    char c;
};

struct _point {
    long x;
    struct _bar* b;
};

int foo(int x, ...)
{
    va_list argp;

    va_start(argp, x);

    struct _point res1 = va_arg(argp, struct _point);

    int result = res1.x + res1.b->c;
    
    res1.x = 0;
    res1.b->c = 0;

    va_end(argp);
    
    return result;
}

int main() {
    struct _point p = {19L, &(struct _bar){3}};
    
    return foo(2, p) + foo(2, p);
}
