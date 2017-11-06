#include <stdlib.h>
#include <stdio.h>
#include "setup.h"

int main() {
  char val, val2, val3, val4;
  int varargs;

  setupStdin("a");
  varargs = scanf("%c", &val);
  cleanupStdin();
  if (varargs != 1 || val != 'a') {
    abort();
  }

  setupStdin("asdf");
  varargs = scanf("%c", &val);
  cleanupStdin();
  if (varargs != 1 || val != 'a') {
    abort();
  }

  setupStdin("asdf");
  varargs = scanf("%c%c%c%c", &val, &val2, &val3, &val4);
  cleanupStdin();
  if (varargs != 4 || val != 'a' || val2 != 's' || val3 != 'd' || val4 != 'f') {
    abort();
  }

  setupStdin("abc");
  varargs = scanf("%cb%c", &val, &val2);
  cleanupStdin();
  if (varargs != 2 || val != 'a' || val2 != 'c') {
    abort();
  }

  setupStdin("abc");
  varargs = scanf("%cc%c", &val, &val2);
  cleanupStdin();
  if (varargs != 1 || val != 'a') {
    abort();
  }

  setupStdin("");
  varargs = scanf("%cc%c", &val, &val2);
  cleanupStdin();
  if (varargs != -1) {
    abort();
  }

  setupStdin("   a ");
  varargs = scanf("%c", &val);
  cleanupStdin();
  if (varargs != 1 || val != ' ') {
    abort();
  }
}
