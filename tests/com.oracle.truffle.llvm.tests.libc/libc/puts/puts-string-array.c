#include <stdio.h>

int main() {
  char *to_print[] = { "hello", "i", "am", "the", "string", "to", "be printed", };
  for (int i = 0; i < 7; i++) {
    puts(to_print[i]);
  }
  return 0;
}
