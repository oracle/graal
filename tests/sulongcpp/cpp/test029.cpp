#include <stdio.h>

void foo() throw(int) {
	printf("Throwing an int!");
	throw 42;
}

void bar() throw() {
	try {
		foo();
	} catch(...) {
		printf("Hander in bar");
	}
}



int main () {
	bar();
}