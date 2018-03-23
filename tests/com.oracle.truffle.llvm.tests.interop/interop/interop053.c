#include <polyglot.h>

typedef void *VALUE;

void *global;

int main() {
	
	void *p = polyglot_import("object");

	global = p;

	void (*returnObject)(void *) = polyglot_import("returnObject");
        returnObject(global);
	return 0;
}
