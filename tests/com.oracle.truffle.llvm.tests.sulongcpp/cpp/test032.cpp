#include <stdio.h>

void foo() throw(int,const char*) {
	printf("Throwing an int!");
	throw "BAR!!";
}

int main () {
	try {
		foo();
	} catch (int a) {
		printf("Caught int %i", a);
	} catch (const char *str) {
		printf("Caught const char * %s", str);
	}
}