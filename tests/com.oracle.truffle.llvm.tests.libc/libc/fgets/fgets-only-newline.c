#include <stdio.h>

int main() {
  char name[L_tmpnam];
  FILE *file = fopen(tmpnam(name), "w");
  if (file == NULL) {
    printf("Failed to open file\n");
    abort();
  }
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
