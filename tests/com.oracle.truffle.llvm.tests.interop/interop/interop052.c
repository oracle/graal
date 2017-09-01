#include <truffle.h>

typedef void *VALUE;

void *global;

int main() {
	global = (void*) 0;
	void *p = truffle_import("object");

	global = p;

	truffle_execute(truffle_import("returnObject"), global);
	return 0;
}