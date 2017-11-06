#include <limits.h>
#include <stdlib.h>
#include <stdio.h>

int main() {
  // only string
  if (printf("asdf") != 4) {
    exit(1);
  }
  if (printf("\n") != 1) {
    exit(2);
  }
  if (printf("") != 0) {
    exit(3);
  }
  // char
  if (printf("asdf%ca\n", 't') != 7) {
    exit(4);
  }
  if (printf("%c%c %c%ca%c\n", 'h', 'e', 'l', 'l', 'o') != 8) {
    exit(5);
  }
  // %
  if (printf("%%\n") != 2) {
    exit(6);
  }
  if (printf("%%%%%c%%\n", 'h') != 5) {
    exit(7);
  }
  // %s
  if (printf("%s %s\n", "hello", "world") != 12) {
    exit(8);
  }
  // %n
  int val;
  if (printf("asdfasdf %nasdf\n", &val) != 14 || val != 9) {
    exit(9);
  }
  if (printf("%n", &val) != 0 || val != 0) {
    exit(10);
  }
  // %d
  if (printf("%d\n", 12345) != 6) {
    exit(11);
  }
  if (printf("%d\n", -12345) != 7) {
    exit(12);
  }
  if (printf("%d\n", INT_MIN) != 12) {
    exit(13);
  }
  if (printf("%d\n", INT_MIN + 1) != 12) {
    exit(14);
  }
  if (printf("%d\n", 0) != 2) {
    exit(15);
  }
  if (printf("%d %d %d %d %d\n", 0, 1, 2, 3, 4) != 10) {
    exit(16);
  }
  // %i
  if (printf("%i %i %i %i %i\n", 0, 1, 2, -3, 4) != 11) {
    exit(16);
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
