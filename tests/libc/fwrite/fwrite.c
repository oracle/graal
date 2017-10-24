
#include <stdio.h>

int main() {
  char buffer[] = { 'x', 'y', 'z', '\n', 0 };
  int written = fwrite(buffer, sizeof(char), sizeof(buffer), stdout);
  printf("written: %d\n", written);
  written = fwrite_unlocked(buffer, sizeof(char), sizeof(buffer), stdout);
  printf("written: %d\n", written);
}
