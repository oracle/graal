#include <stdlib.h>
#include <string.h>

int main() {
  if (strcmp("abc", "abc") != 0) {
    exit(1);
  }
  if (strcmp("abcd", "abce") >= 0) {
    exit(2);
  }
  if (strcmp("abce", "abcd") <= 0) {
    exit(3);
  }
  if (strcmp("", "") != 0) {
    exit(4);
  }
  if (strcmp("abc", "def") >= 0) {
    exit(5);
  }
  if (strcmp("abc", "abcd") >= 0) {
    exit(6);
  }
  if (strcmp("abcd", "abc") <= 0) {
    exit(7);
  }
  if (strcmp("abc", "ABC") <= 0) {
    exit(8);
  }
}
