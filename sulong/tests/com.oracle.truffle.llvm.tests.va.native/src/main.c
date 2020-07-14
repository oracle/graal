#include <stdlib.h>
#include <stdio.h>
#include <math.h>

#include "vahandler.h"
 
double callVAHandler(vahandler vaHandler, int count, ...) {
    va_list args;
    va_start(args, count);
	double res = (*vaHandler)(count, &args);
    va_end(args);
	return res;
}

double callVAHandlers(vahandler vaHandler1, vahandler vaHandler2, int count, ...) {
    va_list args;
    va_start(args, count);
	double res1 = (*vaHandler1)(count / 2, &args);
	double res2 = (*vaHandler2)(count / 2, &args);
    va_end(args);
	return res1 + res2;
}

double sumDoublesLLVM(int count, va_list* args) {
    double sum = 0;
    for (int i = 0; i < count; ++i) {
        double num = va_arg(*args, double);
        sum += num;
    }
    return sum;
}

double testVariousTypesLLVM(int count, va_list* args) {
    double sum = 0;
    for (int i = 0; i < count; ++i) {
        double num1 = va_arg(*args, double);
        int num2 = va_arg(*args, int);
        sum += num1 + num2;
    }
    char* msg = va_arg(*args, char*);
    struct A a = va_arg(*args, struct A);
    struct A b = va_arg(*args, struct A);
    struct A* c = va_arg(*args, struct A*);
    int overflow1 = va_arg(*args, int);
    char* overflow2 = va_arg(*args, char*);
    printf("%s, %d, %f, %d, %f, %d, %f, %d, %s\n", msg, a.x, a.y, b.x, b.y, c->x, c->y, overflow1, overflow2); 
    return sum;
}
 
int main(void) 
{
	printf("Sum of doubles (LLVM)           : %f\n", callVAHandler(sumDoublesLLVM, 6, 1., 2., 3., 4., 5., 6.));
	printf("Sum of doubles (native)         : %f\n", callVAHandler(sumDoublesNative, 6, 1., 2., 3., 4., 5., 6.));
	printf("Sum of doubles (LLVM, native)   : %f\n", callVAHandlers(sumDoublesLLVM, sumDoublesNative, 6, 1., 2., 3., 4., 5., 6.));
	printf("Sum of doubles (native, LLVM)   : %f\n", callVAHandlers(sumDoublesNative, sumDoublesLLVM, 6, 1., 2., 3., 4., 5., 6.));
	printf("Sum of doubles (native, native) : %f\n", callVAHandlers(sumDoublesNative, sumDoublesNative, 6, 1., 2., 3., 4., 5., 6.));
	printf("Sum of doubles (LLVM, LLVM)     : %f\n", callVAHandlers(sumDoublesLLVM, sumDoublesLLVM, 6, 1., 2., 3., 4., 5., 6.));

    struct A a;
    a.x = 10;
    a.y = 3.14;
    struct A b;
    b.x = 11;
    b.y = 4.14;
    struct A* c = malloc(sizeof(struct A));
    c->x = 12;
    c->y = 5.14;
    printf("Test various types (LLVM):\n"); printf("res=%f\n", callVAHandler(testVariousTypesNative, 4, 25.0, 1, 27.3, 2, 26.9, 3, 25.7, 4, "Hello!", a, b, c, 1000, "Hello2!"));
    printf("Test various types (native):\n"); printf("res=%f\n", callVAHandler(testVariousTypesLLVM, 4, 25.0, 1, 27.3, 2, 26.9, 3, 25.7, 4, "Hello!", a, b, c, 1000, "Hello2!"));
}
