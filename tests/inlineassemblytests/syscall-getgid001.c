#include "nanolibc.h"

int main(void) {
  gid_t gid = getgid();
  printf("gid: %d\n", gid);
  return 0;
}
