#include <stdarg.h>
#include <stdio.h>

void foo(int count, ...) {
    va_list ap1;
    
    va_start(ap1, count); 

    printf("%i\n", va_arg(ap1, int));
    printf("%f\n", va_arg(ap1, double));
    printf("%c\n", va_arg(ap1, int));
    printf("%i\n", va_arg(ap1, int));
    printf("%f\n", va_arg(ap1, double));
    
    va_end(ap1);
    
}

int main() {

	foo(3, 1, 2.0, 'a', 4, 5.0);
    return 0;

}