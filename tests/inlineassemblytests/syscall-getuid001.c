#include "nanolibc.h"

int main(void) {
  uid_t uid = getuid();
  printf("uid: %d\n", uid);
  return 0;
}
