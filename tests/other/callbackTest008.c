#include <stdio.h>
#include <truffle.h>

void printArray(int **a);


int main(int argc, char** argv) {
	int *a = malloc(sizeof(int) * 3);
	a[0] = 42;
	a[1] = 43;
	a[2] = 44;

	fprintf(stderr, "Sulong: a[0] = %i\n", a[0]);
	fprintf(stderr, "Sulong: a[1] = %i\n", a[1]);
	fprintf(stderr, "Sulong: a[2] = %i\n", a[2]);
	fprintf(stderr, "Sulong: a = %p\n", a);

	printArray(a);

	return 0;
}
