#include <stdio.h>

int i = 0;

struct A {
	A () {
		printf("CONSTRUCT %d \n", i++);
	}
	A (const A &a) {
		printf("COPY CONSTRUCT %d \n", i++);
	}
	~A() {
		printf("DESTRUCT %d \n", i++);
	}
};

void foo() {
	A a;
	throw a;
}

int main() {
	try {
		foo();
		return 1;
	} catch (...) {
		printf("CATCH\n");
		return 0;
	}
}
