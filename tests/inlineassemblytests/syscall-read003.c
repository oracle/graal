#include "nanolibc.h"

int main(void) {
  char buf[16];
  int fd;
  ssize_t size;
  int i;
  fd = open("LICENSE", O_RDONLY, 0);
  if (fd < 0) {
    perror("Cannot open file");
    return 1;
  }
  while (1) {
    size = read(fd, buf, sizeof(buf));
    if (size < 0) {
      perror("Cannot read file");
      close(fd);
      return 1;
    }
    if (size == 0) {
      break;
    }
    write(STDOUT_FILENO, buf, size);
  }
  close(fd);
  return 0;
}
