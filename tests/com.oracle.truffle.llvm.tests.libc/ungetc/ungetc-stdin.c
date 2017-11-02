#include <stdio.h>

int main() {
  for (int i = 0; i < 26; i++) {
    ungetc('a' + i, stdin);
    putchar(getchar());
  }
  putchar('\n');
}
