#ifndef VAHANDLER_H
#define VAHANDLER_H

#include <stdarg.h>

typedef double (*vahandler) (int, va_list*);

struct A {
	int x;
	double y;
}; 


double sumDoublesNative(int count, va_list* args);

double testVariousTypesNative(int count, va_list* args); 

#endif // VAHANDLER_H
