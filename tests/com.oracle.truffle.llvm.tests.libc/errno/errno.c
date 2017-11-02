#include <stdio.h>
#include <errno.h>

int main() {
  // should be zero but currently fails using Native Sulong (see GR-6577):
  // printf("errno: %d\n", errno);
  errno = EINVAL;
  printf("errno: %d\n", errno);
}
