#include <stdlib.h>
#include <stdio.h>
#include "setup.h"

int main() {
  int val, val2, val3, val4;
  int varargs;

  setupStdin("1");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != 1) {
    abort();
  }

  setupStdin("156456");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != 156456) {
    abort();
  }

  setupStdin(" 42534");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != 42534) {
    abort();
  }

  setupStdin("+56456");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != 56456) {
    abort();
  }

  /*
  setupStdin("++78453");
  varargs = scanf("%d", &val);
  cleanupStdin();
  printf("varargs: %d\n", varargs);
  if (varargs != 0) {
    abort();
  }*/

  setupStdin("+0");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != 0) {
    abort();
  }

  setupStdin("-0");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != 0) {
    abort();
  }

  setupStdin("-54234");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != 1 || val != -54234) {
    abort();
  }

  setupStdin("-54235  4352 +2431 0");
  varargs = scanf("%d%d %d%d", &val, &val2, &val3, &val4);
  cleanupStdin();
  if (varargs != 4 || val != -54235 || val2 != 4352 || val3 != 2431 || val4 != 0) {
    abort();
  }

  setupStdin("");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != -1) {
    printf("asdf\n");
    abort();
  }

  setupStdin(" ");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs != -1) {

    abort();
  }

  setupStdin("z");
  varargs = scanf("%d", &val);
  cleanupStdin();
  if (varargs > 0) {
    abort();
  }

  setupStdin("123434");
  varargs = scanf("%i", &val);
  cleanupStdin();
  if (varargs != 1 || val != 123434) {
    abort();
  }
}
