
#include "harness.h"

int benchmarkWarmupCount() {
  return 25;
}

void benchmarkSetupOnce() {
}

int fib(int n) {
  if (n <= 1) {
    return n;
  } else {
    return fib(n - 1) + fib(n - 2);
  }
}

int benchmarkRun() {
  return fib(30);
}
