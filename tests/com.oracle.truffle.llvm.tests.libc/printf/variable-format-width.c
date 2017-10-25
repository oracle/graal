#include <stdio.h>
#include <stdlib.h>

int main() {
  // char
  if (printf("%*c\n", 5, 'a') != 6) {
    abort();
  }
  // int
  if (printf("%*d\n", 20, 1) != 21) {
    abort();
  }
  if (printf("%*d\n", 20, 12312312) != 21) {
    abort();
  }
  if (printf("%*d\n", 3, 2) != 4) {
    abort();
  }
  if (printf("%*d\n", 3, 221) != 4) {
    abort();
  }
  if (printf("%*d\n", 3, -221) != 5) {
    abort();
  }
  if (printf("%*d\n", 3, 5221) != 5) {
    abort();
  }
  if (printf("%*d\n", 0, 5221) != 5) {
    abort();
  }
  // long
  if (printf("%*ld\n", 3, 221L) != 4) {
    abort();
  }
  if (printf("%*ld\n", 3, -221L) != 5) {
    abort();
  }
  if (printf("%*ld\n", 3, 5221L) != 5) {
    abort();
  }
  // string
  if (printf("%*s\n", 5, "a") != 6) {
    abort();
  }
  if (printf("%*s\n", 5, "asdfg") != 6) {
    abort();
  }
  if (printf("%*s\n", 5, "asdfgasdf") != 10) {
    abort();
  }
  // double
  if (printf("%*f\n", 30, 324.324) != 31) {
    abort();
  }
}
