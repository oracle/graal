#include <stdio.h>
#include <string.h>

int main() {
  char *haystack = "The quick brown fox jumps over the lazy dog";
  char *cur = haystack;
  while (1) {
    cur = strpbrk(cur, "rho");
    if (cur == NULL) {
      break;
    } else {
      printf("found: %c\n", *cur);
      cur++;
    }
  };
}
