#include<stdlib.h>
#include<stdio.h>
#include <truffle.h>


extern "C" 
int test1() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	return p == NULL;
}

extern "C" 
int test2() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	return p != NULL;
}

extern "C" 
int test3() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	int *q = (int*) truffle_virtual_malloc(5 * sizeof(int));
	return p == q;
}

extern "C" 
int test4() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	int *q = (int*) truffle_virtual_malloc(5 * sizeof(int));
	return p != q;
}

extern "C" 
int test5() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	int *q = p;
	return p != q;
}

extern "C" 
int test6() {
	int *p = (int*) truffle_virtual_malloc(5 * sizeof(int));
	int *q = p;
	return p == q;
}

