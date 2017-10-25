#include <stdio.h>
#include <stdlib.h>
#include <limits.h>

int main() {
  // char
  if (printf("asdf%lca\n", 't') != 7) {
    exit(4);
  }
  if (printf("%lc%lc %lc%lca%lc\n", 'h', 'e', 'l', 'l', 'o') != 8) {
    exit(5);
  }

  if (printf("%%\n") != 2) {
    exit(6);
  }
  // %n
  long val;
  if (printf("asdfasdf %lnasdf\n", &val) != 14 || val != 9) {
    exit(9);
  }
  if (printf("%ln", &val) != 0 || val != 0) {
    exit(10);
  }
  // %d
  if (printf("%ld\n", 12345L) != 6) {
    exit(11);
  }
  if (printf("%ld\n", -12345L) != 7) {
    exit(12);
  }
  if (printf("%ld\n", 0L) != 2) {
    exit(15);
  }
  if (printf("%ld %ld %ld %ld %ld\n", 0L, 1L, 2L, 3L, 4L) != 10) {
    exit(16);
  }
  // %i
  if (printf("%li %li %li %li %li\n", 0L, 1L, 2L, -3L, 4L) != 11) {
    exit(16);
  }

  if (printf("%lld", 0LL) != (long long)1) {
    abort();
  }

  // %o
  if (printf("%o\n", 123) != 4) {
    exit(17);
  }
  if (printf("%o\n", 0) != 2) {
    exit(18);
  }
  if (printf("%o\n", -5) != 12) { // 37777777773
    exit(19);
  }
  if (printf("%o\n", INT_MAX) != 12) {
    exit(20);
  }
  if (printf("%o\n", INT_MIN) != 12) {
    exit(21);
  }
  if (printf("%o\n", INT_MIN + 1) != 12) {
    exit(22);
  }
  // %X
  if (printf("%X\n", 5434531) != 7) {
    exit(23);
  }
  if (printf("%X\n", 0) != 2) {
    exit(24);
  }
  if (printf("%X %X\n", INT_MAX, INT_MIN) != 18) {
    exit(25);
  }
  // %x
  if (printf("%x\n", 5434531) != 7) {
    exit(23);
  }
  if (printf("%x\n", 0) != 2) {
    exit(24);
  }
  if (printf("%x %x\n", INT_MAX, INT_MIN) != 18) {
    exit(25);
  }
  // %u
  if (printf("%u %u %u %u\n", INT_MAX, INT_MIN, 0, 45345123) != 33) {
    exit(26);
  }
}
