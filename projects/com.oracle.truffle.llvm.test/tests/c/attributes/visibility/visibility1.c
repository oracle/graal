#include <stdlib.h>

volatile int i1 __attribute__((visibility("protected")));
volatile int i2 __attribute__((visibility("default")));
volatile int i3 __attribute__((visibility("hidden")));
volatile int i4 __attribute__((visibility("protected")));
volatile int i5 __attribute__((visibility("internal")));

int main() {
  i1 = 542;
  i2 = 2341;
  i3 = 1;
  i4 = 4534;
  i5 = 12312;
  if (i1 != 542) {
    abort();
  }
  if (i2 != 2341) {
    abort();
  }
  if (i3 != 1) {
    abort();
  }
  if (i4 != 4534) {
    abort();
  }
  if (i5 != 12312) {
    abort();
  }
}
