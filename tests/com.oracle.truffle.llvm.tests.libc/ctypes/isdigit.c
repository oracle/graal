#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (isdigit('a')) {
    abort();
  }
  if (isdigit('z')) {
    abort();
  }
  if (isdigit('A')) {
    abort();
  }
  if (isdigit('Z')) {
    abort();
  }
  if (isdigit(' ')) {
    abort();
  }
  if (isdigit('!')) {
    abort();
  }
  if (isdigit('@')) {
    abort();
  }
  if (isdigit('[')) {
    abort();
  }
  if (!isdigit('0')) {
    abort();
  }
  if (!isdigit('1')) {
    abort();
  }
  if (!isdigit('5')) {
    abort();
  }
  if (!isdigit('9')) {
    abort();
  }
}
