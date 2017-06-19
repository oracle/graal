#include <stdlib.h>
int __f() { return 3; }

int f() __attribute__((alias("__f")));

int main() {
  if (f() != 3) {
    abort();
  }
}
