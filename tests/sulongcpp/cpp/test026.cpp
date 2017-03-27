#include <stdio.h>

void foo() throw(int) {
	printf("Throwing an int!");
	throw 42;
}


int main () {

	try {
		foo();
	} catch (int i) {
		printf("Caught car exception");
	}
}