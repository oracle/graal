#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (!islower('a')) {
    abort();
  }
  if (!islower('z')) {
    abort();
  }
  if (islower('A')) {
    abort();
  }
  if (islower('Z')) {
    abort();
  }
  if (islower(' ')) {
    abort();
  }
  if (islower('5')) {
    abort();
  }
  if (islower('!')) {
    abort();
  }
  if (islower('@')) {
    abort();
  }
  if (islower('[')) {
    abort();
  }
}
