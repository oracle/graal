#include <truffle.h>

typedef void *VALUE;

void *global = (void*) 0;

int main() {
	void *p = truffle_import("object");

	global = p;

	truffle_execute(truffle_import("returnObject"), global);
	return 0;
}