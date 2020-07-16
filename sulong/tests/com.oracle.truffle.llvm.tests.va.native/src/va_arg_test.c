#include <stdio.h>
#include <stdarg.h>

// Dummy functions whose call sites are manually replaced by va_arg invocations in the LL code
double va_argDouble(va_list args);
int va_argInt(va_list args);
 
double testVaArgDouble(int count, ...) {
    double sum = 0;
    va_list args;
    va_start(args, count);
    for (int i = 0; i < count; ++i) {
        double num = va_argDouble(args);
        sum += num;
    }
    va_end(args);
    return sum;
}

int testVaArgInt(int count, ...) {
    int sum = 0;
    va_list args;
    va_start(args, count);
    for (int i = 0; i < count; ++i) {
        double num = va_argInt(args);
        sum += num;
    }
    va_end(args);
    return sum;
}

int main(void) {
	printf("Test int va_arg    : %d\n", testVaArgInt(8, 1., 2, 3., 4, 5., 6, 7., 8, 9., 10, 11., 12, 13., 14, 15., 16));
	printf("Test double va_arg : %f\n", testVaArgDouble(8, 1., 2, 3., 4, 5., 6, 7., 8, 9., 10, 11., 12, 13., 14, 15., 16));
}
