#include <truffle.h>

void *global;

void **returnPointerToGlobal() {
  return &global;
}

void *returnGlobal() {
  return global;
}

void setter(void **target, void *value) {
	*target = value;
}

int main() {
  return 0;
}