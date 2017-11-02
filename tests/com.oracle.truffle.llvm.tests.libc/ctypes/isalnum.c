#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (!isalnum('a')) {
    abort();
  }
  if (!isalnum('z')) {
    abort();
  }
  if (!isalnum('A')) {
    abort();
  }
  if (!isalnum('Z')) {
    abort();
  }
  if (isalnum(' ')) {
    abort();
  }
  if (!isalnum('0')) {
    abort();
  }
  if (!isalnum('5')) {
    abort();
  }
  if (!isalnum('9')) {
    abort();
  }
  if (isalnum('!')) {
    abort();
  }
  if (isalnum('@')) {
    abort();
  }
  if (isalnum('[')) {
    abort();
  }
}
