#include "nanolibc.h"

int main(void) {
  int fd;
  fd = open("LICENSE", O_RDONLY, 0);
  if (fd < 0) {
    perror("Error opening file");
    return 1;
  }
  close(fd);
  return 0;
}
