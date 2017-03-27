#include <stdio.h>

void foo() throw(int) {
	printf("Throwing an int!");
	throw 42;
}


void car() {
	foo();
}

int main () {
	try {
		car();
	} catch (...) {
		printf("Caught car exception");
	}
}