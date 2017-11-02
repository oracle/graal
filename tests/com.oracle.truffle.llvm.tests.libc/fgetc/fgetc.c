#include <stdio.h>
#include <stdlib.h>

int main() {
  char c;
  FILE *file = fopen(__FILE__, "r");
  while ((c = fgetc(file)) != EOF) {
    putchar(c);
  }
}
