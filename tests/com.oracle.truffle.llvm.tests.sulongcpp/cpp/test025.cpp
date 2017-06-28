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

void tar() {
	try {
		foo();
	} catch (int i) {
		printf("Hander in tar");
	}
}

void car() {
	foo();
}

int main () {
	bar();
	tar();
	try {
		car();
	} catch (...) {
		printf("Caught car exception");
	}
}