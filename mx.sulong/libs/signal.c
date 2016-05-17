#include <stdio.h>

void (*signal(int sig, void (*func)(int)))(int) {
  fprintf(stderr, "Sulong does not support signals, registering a signal handler over "
                  "the signal function has no effect!\n");
  return func;
}
