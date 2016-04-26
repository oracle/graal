#include <math.h>
#include <stdio.h>
#include <stdlib.h>

int *a;

int *func() {
  static int initialized = 0;
  if (!initialized) {
    a = (int *)malloc(sizeof(int));
    initialized = 1;
    *a = 4;
  }
  return a;
}

int main() {
  int *nr = func();
  nr[0]++;
  (*a)++;
  return *func();
}
