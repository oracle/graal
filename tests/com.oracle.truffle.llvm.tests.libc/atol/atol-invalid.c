#include <stdlib.h>

int main() {
  if (atol("a") != 0) {
    exit(1);
  }
  if (atol("") != 0) {
    exit(2);
  }
}
