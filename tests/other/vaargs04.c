#include <stdarg.h>
#include <stdio.h>

void foo(int count, ...) {
    va_list ap1;
    
    va_start(ap1, count); 

    printf("%f\n", va_arg(ap1, double));
    printf("%f\n", va_arg(ap1, double));
    printf("%f\n", va_arg(ap1, double));
    printf("%f\n", va_arg(ap1, double));
    printf("%f\n", va_arg(ap1, double));

    va_end(ap1);
    
}

int main() {

	foo(3, 1.0, 2.0, 3.0, 4.0, 5.0);
    return 0;

}