#include <stdlib.h>

int main() {
  int val = 1;
  int old;

  old = __sync_lock_test_and_set(&val, 2); // atomicrmw xchg
  if (old != 1 || val != 2) {
    abort();
  }

  old = __sync_fetch_and_add(&val, 2); // atomicrmw add
  if (old != 2 || val != 4) {
    abort();
  }

  old = __sync_fetch_and_sub(&val, 1); // atomicrmw sub
  if (old != 3 || val != 3) {
    abort();
  }

  old = __sync_fetch_and_and(&val, 2); // atomicrmw and
  if (old != 3 || val != 2) {
    abort();
  }

  old = __sync_fetch_and_or(&val, 1); // atomicrmw or
  if (old != 2 || val != 3) {
    abort();
  }
}
