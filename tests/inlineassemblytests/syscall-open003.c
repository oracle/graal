#include "nanolibc.h"

int main(void) {
  char cwd[257];
  int fd;
  getcwd(cwd, sizeof(cwd));
  printf("path: %s\n", cwd);
  fd = open(cwd, O_WRONLY, 0);
  if (fd < 0) {
#if 0
    printf("Cannot open file: %d\n", errno);
#endif
    return 0;
  }
  close(fd);
  return 1;
}
