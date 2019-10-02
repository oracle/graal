#include <stdio.h>

long result = 0;

int fib(int n) {
  if (n <= 1) {
    return 1;
  } else {
    return fib(n - 1) + fib(n - 2);
  }
}

int main() {
  fib(20);
  return 0;
}

