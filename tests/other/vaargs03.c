#include <stdarg.h>
#include <stdio.h>

void foo(int count, ...) {
    va_list ap1;
    va_list ap2;
    
    va_start(ap1, count); 

    printf("%i\n", va_arg(ap1, int));
    printf("%f\n", va_arg(ap1, double));
    printf("%c\n", va_arg(ap1, int));

    va_copy(ap2, ap1);

    printf("%i\n", va_arg(ap1, int));
    printf("%f\n", va_arg(ap1, double));
    
    printf("%i\n", va_arg(ap2, int));
    printf("%f\n", va_arg(ap2, double));
    

    va_end(ap2);
    va_end(ap1);
    
}

int main() {

	foo(3, 1, 2.0, 'a', 4, 5.0);
    return 0;

}