#include <stdlib.h>
#include <string.h>

int main() {
  if (memcmp("abc", "abc", 3) != 0) {
    exit(1);
  }
  if (memcmp("abcd", "abce", 4) >= 0) {
    exit(2);
  }
  if (memcmp("abce", "abcd", 4) <= 0) {
    exit(3);
  }
  if (memcmp("", "", 1) != 0) {
    exit(4);
  }
  if (memcmp("abc", "def", 3) >= 0) {
    exit(5);
  }
  if (memcmp("abc", "abcd", 4) >= 0) {
    exit(6);
  }
  if (memcmp("abcd", "abc", 4) <= 0) {
    exit(7);
  }
  if (memcmp("abc", "ABC", 3) <= 0) {
    exit(8);
  }
}
