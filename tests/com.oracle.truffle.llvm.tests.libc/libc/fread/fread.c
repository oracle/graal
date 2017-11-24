#include <stdio.h>
#include <stdlib.h>

int main() {
  char name[L_tmpnam];
  FILE *file = fopen(tmpnam(name), "w");
  if (file == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  fputs("a asd a xdfasdf abn asdfasdf asdfdfaa", file);
  fclose(file);
  FILE *read = fopen(name, "r");
  if (read == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  char buf[4];
  int count;
  while ((count = fread(buf, 1, 3, read)) != 0) {
    buf[count] = '\0';
    printf("%s (%d chars)\n", buf, count);
  }
  fclose(read);
  unlink(name);
}
