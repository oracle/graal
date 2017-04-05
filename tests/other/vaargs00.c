#include <stdarg.h>

double foo(int count, ...) {
    va_list ap1;
    
    int j;
    double tot = 0;
    
    va_start(ap1, count); 

    double v = va_arg(ap1, double);

    

    for(j=1; j<count; j++)
        tot+=va_arg(ap1, double); //Requires the type to cast to. Increments ap to the next argument.
    
    va_end(ap1);
    
    
    return (v + tot)/count;
}

int main() {

	return (int) foo(3, 1.0, 2.0, 3.0);

}