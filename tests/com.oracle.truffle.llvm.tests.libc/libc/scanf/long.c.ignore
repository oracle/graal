#include <stdlib.h>
#include <stdio.h>
#include "setup.h"

int main() {
  long val, val2, val3, val4;
  int varargs;

  setupStdin("1");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 1) {
    exit(1);
  }

  setupStdin("156456");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 156456) {
    exit(2);
  }

  setupStdin(" 42534");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 42534) {
    exit(3);
  }

  setupStdin("+56456");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 56456) {
    exit(4);
  }

  setupStdin("+0");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 0) {
    exit(5);
  }

  setupStdin("-0");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 0) {
    exit(6);
  }

  setupStdin("-54234");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != -54234) {
    exit(7);
  }

  setupStdin("-54235  4352 +2431 0");
  varargs = scanf("%ld%ld %ld%ld", &val, &val2, &val3, &val4);
  cleanupStdin();
  if (varargs != 4 || val != -54235 || val2 != 4352 || val3 != 2431 || val4 != 0) {
    exit(8);
  }

  setupStdin("");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != -1) {
    exit(9);
  }

  setupStdin(" ");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != -1) {
    exit(10);
  }

  setupStdin("z");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs > 0) {
    exit(11);
  }

  setupStdin("9223372036854775807");
  varargs = scanf("%ld", &val);
  cleanupStdin();
  if (varargs != 1 || val != 9223372036854775807) {
    exit(12);
  }
}
