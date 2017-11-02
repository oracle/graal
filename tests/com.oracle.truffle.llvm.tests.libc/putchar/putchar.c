#include <stdio.h>

int main() {
  char ch;
  for (char ch = 'A'; ch <= 'Z'; ch++) {
    putchar(ch);
    putchar_unlocked(ch);
  }
  putchar('\n');
  return 0;
}
