#include <stdio.h>
#include <errno.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fclose(file);
  freopen(name, "w", stderr);
  for (int i = 0; i < 10; i++) {
    errno = i;
    perror("hello world!");
  }
  fclose(stderr);
  file = fopen(name, "r");
  char buf[500];
  while (fgets(buf, 500, file) != NULL) {
    puts(buf);
  }
  fclose(file);
}
