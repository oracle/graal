#include <truffle.h>

typedef void *VALUE;

void *global;
void **global2;

int main() {
	
	void *p = truffle_import("object");

	global = p;
	global2 = &global;

	truffle_execute(truffle_import("returnObject"), *global2);
	return 0;
}