#include <stdio.h>

void foo() throw(int) {
	printf("Throwing an int!\n");
	throw 42;
}


int main () {
	try {
		foo();
		printf("afterfoo\n");
	} catch (...) {
		printf("Caught int exception\n");
	}
	printf("end\n");
}
