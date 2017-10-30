#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>

void hook1(void) {
  printf("atexit hook 1\n");
  fflush(stdout);
}

void hook2(void) {
  printf("atexit hook 2\n");
  fflush(stdout);
}

int main(void) {
  atexit(hook1);
  atexit(hook2);
  printf("main\n");
  fflush(stdout);
  _exit(0);
}
