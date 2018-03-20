#include <polyglot.h>

typedef void *VALUE;

void *global = (void*) 0;

int main() {
	void *p = polyglot_import("object");

	global = p;

        void (*returnObject)(void *) = polyglot_import("returnObject");
        returnObject(global);
	return 0;
}
