#include <unistd.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <sys/types.h>

int main(int argc, char **argv) {
  int file = 0;
  if ((file = open(argv[0], O_RDONLY)) < -1)
    return 1;

  struct stat fileStat;
  if (stat(argv[0], &fileStat) < 0)
    return 1;
  if (fileStat.st_size <= 0) {
    return 2;
  }
  if (lstat(argv[0], &fileStat) < 0)
    return 3;
  if (fileStat.st_size <= 0) {
    return 4;
  }
  if (fstat(file, &fileStat) < 0)
    return 5;
  if (fileStat.st_size <= 0) {
    return 6;
  }
  return 0;
}
