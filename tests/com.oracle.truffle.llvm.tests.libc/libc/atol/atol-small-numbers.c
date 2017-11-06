#include <stdlib.h>

int main() {
  if (atol("0") != 0L) {
    abort();
  }
  if (atol("1") != 1L) {
    abort();
  }
  if (atol("123456") != 123456L) {
    abort();
  }
  if (atol("-123456") != -123456L) {
    abort();
  }
}
