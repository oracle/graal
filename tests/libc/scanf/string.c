#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "setup.h"

int main() {
  char val[200], val2[200], val3[200], val4[200];
  int varargs;

  setupStdin("a");
  varargs = scanf("%s", val);
  if (varargs != 1 || strcmp("a", val) != 0) {
    abort();
  }

  setupStdin("asdf");
  varargs = scanf("%s", val);
  if (varargs != 1 || strcmp("asdf", val) != 0) {
    abort();
  }

  setupStdin("asdf y bcvb qwea");
  varargs = scanf("%s%s%s%s", val, val2, val3, val4);
  if (varargs != 4 || strcmp("asdf", val) != 0 || strcmp("y", val2) != 0 || strcmp("bcvb", val3) != 0 || strcmp("qwea", val4) != 0) {
    abort();
  }

  setupStdin("kjl qwer yxcv z");
  varargs = scanf("%s %s %s %s", val, val2, val3, val4);
  if (varargs != 4 || strcmp("kjl", val) != 0 || strcmp("qwer", val2) != 0 || strcmp("yxcv", val3) != 0 || strcmp("z", val4) != 0) {
    abort();
  }

  setupStdin("asdf yxcv hff");
  varargs = scanf("%s yxcv %s", val, val2);
  if (varargs != 2 || strcmp("asdf", val) != 0 || strcmp("hff", val2) != 0) {
    abort();
  }

  setupStdin("uipo yxcv hff");
  varargs = scanf("%syxcv%s", val, val2);
  if (varargs != 1 || strcmp("uipo", val) != 0) {
    printf("%d", varargs);
    abort();
  }

  setupStdin("");
  varargs = scanf("%s %s", &val, &val2);
  if (varargs != -1) {
    abort();
  }

  setupStdin("    ");
  varargs = scanf("%s %s", &val, &val2);
  if (varargs != -1) {
    abort();
  }

  setupStdin("  a  b   ");
  varargs = scanf("%s %s", &val, &val2);
  if (varargs != 2 || strcmp("a", val) != 0 || strcmp("b", val2) != 0) {
    abort();
  }

  setupStdin("  c    ");
  varargs = scanf("%s %s", &val, &val2);

  if (varargs != 1 || strcmp("c", val) != 0) {
    abort();
  }
}
