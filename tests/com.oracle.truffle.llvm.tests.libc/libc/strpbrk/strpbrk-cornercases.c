#include <stdlib.h>
#include <string.h>

int main() {
  char *haystack = "The quick brown fox jumps over the lazy dog";
  if (strpbrk(haystack, "") != NULL) {
    abort();
  }
  if (strpbrk("", haystack) != NULL) {
    abort();
  }
  if (strpbrk("", "") != NULL) {
    abort();
  }
}
