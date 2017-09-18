#include<stdlib.h>
#include<stdio.h>
#include <truffle.h>

extern "C" 
int test1() {
	int **p = (int**) truffle_virtual_malloc(5 * sizeof(int*));
	int *a1 = (int *) malloc(5 * sizeof(int));
	int *a2 = (int *) malloc(5 * sizeof(int));

	p[1] = a1;
	p[2] = a2;

	a1[2] = 42;
	a2[3] = 43;

	return p[1][2];
}

extern "C" 
int test2() {
	int **p = (int**) truffle_virtual_malloc(5 * sizeof(int*));
	int *a1 = (int *) malloc(5 * sizeof(int));
	int *a2 = (int *) malloc(5 * sizeof(int));

	p[1] = a1;
	p[2] = a2;

	a1[2] = 42;
	a2[3] = 43;

	return p[2][3];
}
