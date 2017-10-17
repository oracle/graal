#include <stdio.h>
#include <stdlib.h>

void func() { printf("destruct\n"); }

__attribute__((constructor)) void ctor() {
  printf("construct\n");
  atexit(func);
}

__attribute__((destructor)) void dtor() { printf("destruct\n"); }

int main() {
  printf("main\n");
  return 0;
}
