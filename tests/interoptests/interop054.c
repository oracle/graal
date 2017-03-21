#include <truffle.h>

typedef void *VALUE;

void *global;

int main() {
	
	void *p = truffle_import("object");

	global = p;

	void **pp = &global; // should not harm us, pp is in the frame

	truffle_execute(truffle_import("returnObject"), global);
	return 0;
}