#include <stdlib.h>
#include <stdio.h>

void hook1(void) { printf("atexit hook 1\n"); }

void hook2(void) { printf("atexit hook 2\n"); }

int main(void) {
  atexit(hook1);
  atexit(hook2);
  printf("main\n");
  return 0;
}
