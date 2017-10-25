#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (isspace('a')) {
    abort();
  }
  if (isspace('z')) {
    abort();
  }
  if (isspace('A')) {
    abort();
  }
  if (isspace('Z')) {
    abort();
  }
  if (isspace('5')) {
    abort();
  }
  if (isspace('!')) {
    abort();
  }
  if (isspace('@')) {
    abort();
  }
  if (isspace('[')) {
    abort();
  }
  if (!isspace(' ')) {
    abort();
  }
  if (!isspace('\t')) {
    abort();
  }
  if (!isspace('\n')) {
    abort();
  }
  if (!isspace('\v')) {
    abort();
  }
  if (!isspace('\f')) {
    abort();
  }
  if (!isspace('\r')) {
    abort();
  }
}
