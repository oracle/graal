#include <stdarg.h>

double foo(int count, ...) {
    va_list ap1;
    va_list ap2;
    int j;
    double tot = 0;
    
    va_start(ap1, count); 

    double v = va_arg(ap1, double);

    va_copy(ap2, ap1);

    for(j=1; j<count; j++)
        tot+=va_arg(ap2, double); //Requires the type to cast to. Increments ap to the next argument.
    
    va_end(ap1);
    va_end(ap2);
    
    return (v + tot)/count;
}

int main() {

	return (int) foo(3, 1.0, 2.0, 3.0);

}