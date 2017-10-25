#include <stdlib.h>

int main() {
  if (atoi("a") != 0) {
    exit(1);
  }
  if (atoi("") != 0) {
    exit(2);
  }
  if (atoi("0") != 0) {
    exit(3);
  }
  if (atoi("1") != 1) {
    exit(4);
  }
  if (atoi("123456") != 123456) {
    exit(5);
  }
  if (atoi("-123456") != -123456) {
    exit(6);
  }
}
