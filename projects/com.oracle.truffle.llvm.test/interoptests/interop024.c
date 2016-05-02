#include<truffle.h>

int main() {
	foo("foreign");
	return foo("foreign2");
}

int foo(const char *name) {
	void *obj = truffle_import_cached(name);
	return truffle_read_i(obj, "valueI");
}
