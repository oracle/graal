#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (!isalpha('a')) {
    abort();
  }
  if (!isalpha('z')) {
    abort();
  }
  if (!isalpha('A')) {
    abort();
  }
  if (!isalpha('Z')) {
    abort();
  }
  if (isalpha(' ')) {
    abort();
  }
  if (isalpha('5')) {
    abort();
  }
  if (isalpha('!')) {
    abort();
  }
  if (isalpha('@')) {
    abort();
  }
  if (isalpha('[')) {
    abort();
  }
}
