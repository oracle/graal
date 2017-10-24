#include <stdio.h>
#include <stdlib.h>

int main() {
  char c;
  FILE *file = fopen(__FILE__, "r");
  while ((c = getc_unlocked(file)) != EOF) {
    putchar(c);
  }
}
