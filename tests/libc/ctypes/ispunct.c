#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>

int main() {
  if (ispunct('a')) {
    abort();
  }
  if (ispunct('z')) {
    abort();
  }
  if (ispunct('A')) {
    abort();
  }
  if (ispunct('Z')) {
    abort();
  }
  if (ispunct(' ')) {
    abort();
  }
  if (ispunct('5')) {
    abort();
  }
  if (!ispunct('!')) {
    abort();
  }
  if (!ispunct('@')) {
    abort();
  }
  if (!ispunct('[')) {
    abort();
  }
}
