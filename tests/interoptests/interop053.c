#include <truffle.h>

typedef void *VALUE;

void *global;

int main() {
	
	void *p = truffle_import("object");

	global = p;

	truffle_execute(truffle_import("returnObject"), global);
	return 0;
}