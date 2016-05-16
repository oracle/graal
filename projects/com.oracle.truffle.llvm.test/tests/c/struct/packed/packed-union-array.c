#include <stdlib.h>

#pragma pack(1)

union test {
  short a;
  struct {
    char x;
    char y;
    char z;
  } b;
};

union test arr[2];

int main() {
  arr[0].a = 1234;
  arr[1].a = 24212;
  int *ptr = (int *)&arr[0].b.z;
  if (*ptr != 6198272) {
    abort();
  }
}
