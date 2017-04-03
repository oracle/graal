#include <stdio.h>

typedef void (*myfunc)();

int nullPointerFunctionTest(void (*foo)());

int main(int argc, char** argv) {
	myfunc foo = (myfunc) 0;
	return nullPointerFunctionTest(foo);
}
