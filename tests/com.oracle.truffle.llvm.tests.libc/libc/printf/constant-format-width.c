#include <stdio.h>
#include <stdlib.h>

int main() {
  // char
  if (printf("%5c\n", 'a') != 6) {
    abort();
  }
  // int
  if (printf("%20d\n", 1) != 21) {
    abort();
  }
  if (printf("%20d\n", 12312312) != 21) {
    abort();
  }
  if (printf("%3d\n", 2) != 4) {
    abort();
  }
  if (printf("%3d\n", 221) != 4) {
    abort();
  }
  if (printf("%3d\n", -221) != 5) {
    abort();
  }
  if (printf("%3d\n", 5221) != 5) {
    abort();
  }
  if (printf("%0d\n", 5221) != 5) {
    abort();
  }
  // long
  if (printf("%3ld\n", 221L) != 4) {
    abort();
  }
  if (printf("%3ld\n", -221L) != 5) {
    abort();
  }
  if (printf("%3ld\n", 5221L) != 5) {
    abort();
  }
  // string
  if (printf("%5s\n", "a") != 6) {
    abort();
  }
  if (printf("%5s\n", "asdfg") != 6) {
    abort();
  }
  if (printf("%5s\n", "asdfgasdf") != 10) {
    abort();
  }
  // double
  if (printf("%30f\n", 324.324) != 31) {
    abort();
  }
}
