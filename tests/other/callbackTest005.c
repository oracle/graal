#include <stdio.h>
#include <truffle.h>

struct container {
  int (*callback)(int p1, int p2);
  int p1;
};

void store_native_function(struct container *);


int main(int argc, char** argv) {
	struct container c;
	store_native_function(&c);
	return c.callback(22, 20);
}
