#include <truffle.h>

typedef void *VALUE;

void *global;

int bar = 5;

int main() {
	
	void *p = truffle_import("object");

	global = &bar;
	global = p;
	global = &bar;
	global = p;
	global = &bar;
	global = p;

	truffle_execute(truffle_import("returnObject"), global);
	return 0;
}