#include <stdlib.h>

int main() {
  for (int i = 0; i < 10000; i++) {
    int random = rand();
    if (random < 0) {
      abort();
    }
  }
}
