#include <stdio.h>
#include <math.h>

typedef double (*test_type)(double);

#define SIZE 4

test_type getFunction(int i) {
	int val = i % SIZE;
	switch (val) {
		case 0: return &log2;
		case 1: return &floor;
		case 2: return &fabs;
		case 3: return &ceil;
	}
	return 0;
}

int callFunction() {
	double val;
	int i;
	test_type func;
	double currentVal = 2342;
	for (i = 0; i < 1000; i++) {
		currentVal = getFunction(i)(currentVal) > 4353423;
	}
	return currentVal;	
}

int main() {
	int i;
	int sum = 0;
	for (i = 0; i < 1000; i++) {
		sum += callFunction();
	}
	return sum == 0;
}
