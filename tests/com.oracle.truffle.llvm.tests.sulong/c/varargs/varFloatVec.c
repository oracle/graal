#include <stdarg.h>

struct _point {
    float x;
    float y;
};

int foo(int x, ...) {
    va_list argp;

    va_start(argp, x);

    struct _point res1 = va_arg(argp, struct _point);

    va_end(argp);
    
    return (int)(res1.x + res1.y);
}

int main() {
    struct _point p = {23.f, 13.f};

    /*
     * p is passed in the vararg by using a vector of 2 floats:
     *
     * call void (i32, ...) @foo(i32 1, <2 x float> %5)
     */
    return foo(1, p);
}
