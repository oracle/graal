#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (!isxdigit('a')) {
    abort();
  }
  if (!isxdigit('d')) {
    abort();
  }
  if (!isxdigit('f')) {
    abort();
  }
  if (isxdigit('z')) {
    abort();
  }
  if (!isxdigit('A')) {
    abort();
  }
  if (!isxdigit('D')) {
    abort();
  }
  if (!isxdigit('F')) {
    abort();
  }
  if (isxdigit('Z')) {
    abort();
  }
  if (isxdigit(' ')) {
    abort();
  }
  if (!isxdigit('0')) {
    abort();
  }
  if (!isxdigit('5')) {
    abort();
  }
  if (!isxdigit('9')) {
    abort();
  }
  if (isxdigit('!')) {
    abort();
  }
  if (isxdigit('@')) {
    abort();
  }
  if (isxdigit('[')) {
    abort();
  }
}
