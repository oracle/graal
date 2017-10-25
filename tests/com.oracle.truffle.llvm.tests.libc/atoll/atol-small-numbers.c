#include <stdlib.h>

int main() {
  if (atoll("0") != 0L) {
    abort();
  }
  if (atoll("1") != 1L) {
    abort();
  }
  if (atoll("123456") != 123456L) {
    abort();
  }
  if (atoll("-123456") != -123456L) {
    abort();
  }
}
