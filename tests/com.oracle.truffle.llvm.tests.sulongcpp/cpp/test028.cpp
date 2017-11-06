#include <stdio.h>

void foo() throw(int) {
	printf("Throwing an int!\n");
	throw 42;
}


int main () {
	try {
		foo();
	} catch (...) {
		printf("Caught int exception\n");
	}
}
