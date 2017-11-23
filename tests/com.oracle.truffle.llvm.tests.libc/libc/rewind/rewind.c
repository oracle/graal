#include <stdio.h>
#include <stdlib.h>

int main() {
  char name[L_tmpnam];
  FILE *file = fopen(tmpnam(name), "w");
  if (file == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  fputs("a asd a xdfasdf abn asdfasdf asdfdf\n", file);
  fclose(file);
  FILE *read = fopen(name, "r");
  if (read == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  char buf[20];
  while (fgets(buf, 20, read) != NULL) {
    printf("%s\n", buf);
  }
  rewind(read);
  while (fgets(buf, 20, read) != NULL) {
    printf("%s\n", buf);
  }
  printf("%ld\n", ftell(read));
  fclose(read);
  unlink(name);
}
