#include <stdio.h>
#include <stdlib.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fputs("a asd a xdfasdf abn asdfasdf asdfdfaa", file);
  fclose(file);
  FILE *read = fopen(name, "r");
  char buf[4];
  int count;
  while ((count = fread(buf, 1, 3, read)) != 0) {
    buf[count] = '\0';
    printf("%s (%d chars)\n", buf, count);
  }
}
