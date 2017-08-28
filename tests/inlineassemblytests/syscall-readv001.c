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
  size = readv(fd, iov, 2);
  printf("size: %d\n", size);
  if (size < 0) {
    perror("Cannot read file");
    return 1;
  }
  write(STDOUT_FILENO, buf1, size < sizeof(buf1) ? size : sizeof(buf1));
  write(STDOUT_FILENO, buf2, size < sizeof(buf1) ? 0 : (size - sizeof(buf1)));
  close(fd);
  return 0;
}
