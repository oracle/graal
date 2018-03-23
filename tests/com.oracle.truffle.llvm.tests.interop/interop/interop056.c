#include <polyglot.h>

typedef void *VALUE;

void *global;

int bar = 5;

int main() {
	
	void *p = polyglot_import("object");

	global = &bar;
	global = p;
	global = &bar;
	global = p;
	global = &bar;
	global = p;

	void (*returnObject)(void *) = polyglot_import("returnObject");
        returnObject(global);
	return 0;
}
