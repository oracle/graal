#include <string.h>
#include <stdlib.h>

int main() {
  int val1 = 432459243;
  int val2 = 234123423;
  if (memcmp(&val1, &val2, sizeof(int) / sizeof(char)) == 0) {
    abort();
  }
  if (memcmp(&val1, &val1, sizeof(int) / sizeof(char)) != 0) {
    abort();
  }
  if (memcmp(&val2, &val2, sizeof(int) / sizeof(char)) != 0) {
    abort();
  }
}
