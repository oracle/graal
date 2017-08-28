#include "nanolibc.h"

int main(void) {
  char cwd[257];
  char path[257];
  int fd;
  getcwd(cwd, sizeof(cwd));
  sprintf(path, "%s/nonexistent", cwd);
  printf("path: %s\n", path);
  fd = open(path, O_RDONLY, 0);
  if (fd < 0) {
#if 0
    printf("Cannot open file: %d\n", errno);
#endif
    return 0;
  }
  close(fd);
  return 1;
}
