#include <stdlib.h>

int main() {
  if (atoll("a") != 0) {
    exit(1);
  }
  if (atoll("") != 0) {
    exit(2);
  }
}
