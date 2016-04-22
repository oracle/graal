#include<truffle.h>

int main() {
	int *obj = (int*) truffle_import("foreign");
	obj[0] = 30;
	obj[1] = 31;
	obj[2] = 32;
	obj[3] = 33;
	obj[4] = 34;
	return 0;
}
