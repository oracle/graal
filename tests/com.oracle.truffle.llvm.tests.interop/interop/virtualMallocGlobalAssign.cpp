#include<stdlib.h>
#include <truffle.h>

long *p;

extern "C" 
long test() {
	static long **pp;
	p = (long*) truffle_virtual_malloc(sizeof(long));
	pp = &p;
	**pp = 42;
	return **pp;
}
