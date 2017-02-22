#include <stdio.h>

void foo() throw() {
	printf("Throwing an int!");
	throw 42;
}


int main () {
	try {
		foo();
		printf("afterfoo");
	} catch (...) {
		printf("Caught car exception");
	}
	printf("end");
}