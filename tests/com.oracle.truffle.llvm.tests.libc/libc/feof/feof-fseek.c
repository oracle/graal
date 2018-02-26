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
  fseek(read, 0, SEEK_END);
  while (1) {
    int c = fgetc(read);
    if (feof(read)) {
      break;
    }
    putchar(c);
  }
  putchar('\n');
  fclose(read);
  unlink(name);
}
