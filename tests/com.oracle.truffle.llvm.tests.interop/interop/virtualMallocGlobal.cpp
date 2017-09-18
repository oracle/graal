#include<stdlib.h>
#include <truffle.h>

long *p;

extern "C" 
long test() {
	p = (long*) truffle_virtual_malloc(sizeof(long));
	*p = 42;
	return *p;
}
