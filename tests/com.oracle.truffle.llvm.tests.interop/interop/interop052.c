#include <polyglot.h>

typedef void *VALUE;

void *global;

int main() {
	global = (void*) 0;
	void *p = polyglot_import("object");

	global = p;

	void (*returnObject)(void *) = polyglot_import("returnObject");
	returnObject(global);
	return 0;
}
