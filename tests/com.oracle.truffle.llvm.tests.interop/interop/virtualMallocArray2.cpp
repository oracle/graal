#include<stdlib.h>
#include<stdio.h>
#include <truffle.h>


extern "C" 
int test() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	p[2] = 42;
	return *(p+2);
}
