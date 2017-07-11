#include <stdarg.h>

struct _point {
    char x;
    char y;
};

int foo(int x, ...) {
    va_list argp;

    va_start(argp, x);
    
    int res = 0;

    for(int i = 0; i < 10; i++) {
        res += va_arg(argp, struct _point).x;
    }

    va_end(argp);

    return res;
}

int main() {
    struct _point p1 = {2, 3};
    struct _point p2 = {5, 7};

    return foo(1, p1, p2, p1, p2, p1, p2, p1, p2, p1, p2);
}
