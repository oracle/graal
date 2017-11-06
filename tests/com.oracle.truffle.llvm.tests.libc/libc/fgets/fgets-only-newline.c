#include <stdio.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fputc('\n', file);
  fclose(file);
  FILE *read = fopen(name, "r");
  char buf[200];
  while (fgets(buf, 200, read) != NULL) {
    printf("%s\n", buf);
  }
  fclose(read);
  unlink(name);
}
