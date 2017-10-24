#include "nanolibc.h"

int main(void) {
  char buf[64];
  int fd;
  ssize_t size;
  off_t offset;
  int i;

  fd = open("LICENSE", O_RDONLY, 0);
  if (fd < 0) {
    perror("Cannot open file");
    return 1;
  }

  size = read(fd, buf, sizeof(buf));
  printf("size: %ld\n", size);
  if (size < 0) {
    perror("Cannot read file");
    close(fd);
    return 1;
  }
  write(STDOUT_FILENO, buf, size);

  offset = lseek(fd, 65536, SEEK_SET);
  printf("offset: %ld\n", offset);
  if (offset == (off_t)-1) {
    perror("Cannot read file offset");
    close(fd);
    return 1;
  }

  close(fd);
  return 0;
}
