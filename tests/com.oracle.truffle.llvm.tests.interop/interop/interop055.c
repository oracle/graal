#include <polyglot.h>

typedef void *VALUE;

void *global;
void **global2;

int main() {
	
	void *p = polyglot_import("object");

	global = p;
	global2 = &global;

	void (*returnObject)(void *) = polyglot_import("returnObject");
        returnObject(*global2);
	return 0;
}
