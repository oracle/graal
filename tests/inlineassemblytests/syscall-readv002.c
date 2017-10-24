#include "nanolibc.h"

int main(void) {
  char buf1[16];
  char buf2[32];
  int fd;
  ssize_t size;
  struct iovec iov[2];
  int i;
  fd = open("LICENSE", O_RDONLY, 0);
  if (fd < 0) {
    perror("Cannot open file");
    return 1;
  }
  iov[0].iov_base = buf1;
  iov[0].iov_len = sizeof(buf1);
  iov[1].iov_base = buf2;
  iov[1].iov_len = sizeof(buf2);
  size = readv(fd, iov, -1);
  printf("size: %d\n", size);
  if (size < 0) {
#if 0
    printf("Error: %d\n", errno);
#endif
    return 0;
  }
  close(fd);
  return 1;
}
