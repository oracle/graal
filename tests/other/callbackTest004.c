#include <stdio.h>
#include <truffle.h>

struct container {
  int (*callback)(int p1, int p2);
  int p1;
};

int call_callback2(struct container *p);

int add(int a, int b) {
	return a + b;
}



int main(int argc, char** argv) {
	struct container c;
	c.callback = add;
	return call_callback2(&c);
}
