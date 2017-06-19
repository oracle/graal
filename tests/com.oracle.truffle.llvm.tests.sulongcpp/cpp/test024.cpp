#include <stdio.h>

struct A {
	~A() {
		printf("DESTRUCT");
	}
};

void foo() {
	A a;
	throw "BAM";
}

int main() {
	try {
		foo();
		return 1;
	} catch (...) {
		return 0;
	}
}
