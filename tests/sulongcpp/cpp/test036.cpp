#include <stdio.h>

struct A {
	A () {
		printf("CONSTRUCT\n");
	}
	A (const A &a) {
		printf("COPY CONSTRUCT\n");
	}
	~A() {
		printf("DESTRUCT\n");
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
	} catch (A &a) {
		printf("CATCH\n");
		return 0;
	}
}
