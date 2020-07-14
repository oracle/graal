#include <stdio.h>

#include "vahandler.h"

double sumDoublesNative(int count, va_list* args) {
    double sum = 0;
    for (int i = 0; i < count; ++i) {
        double num = va_arg(*args, double);
        sum += num;
    }
    return sum;
}

double testVariousTypesNative(int count, va_list* args) {
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
