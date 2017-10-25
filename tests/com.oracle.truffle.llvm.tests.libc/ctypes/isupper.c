#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (isupper('a')) {
    abort();
  }
  if (isupper('z')) {
    abort();
  }
  if (!isupper('A')) {
    abort();
  }
  if (!isupper('Z')) {
    abort();
  }
  if (isupper(' ')) {
    abort();
  }
  if (isupper('5')) {
    abort();
  }
  if (isupper('!')) {
    abort();
  }
  if (isupper('@')) {
    abort();
  }
  if (isupper('[')) {
    abort();
  }
}
