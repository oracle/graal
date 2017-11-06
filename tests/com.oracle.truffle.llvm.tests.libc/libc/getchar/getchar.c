#include <stdio.h>
#include <stdlib.h>

int main() {
  char c;
  FILE *file = freopen(__FILE__, "r", stdin);
  while ((c = getchar()) != EOF) {
    putchar(c);
  }
}
