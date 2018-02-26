#include <stdio.h>

int main() {
  char name[L_tmpnam];
  FILE *file = fopen(tmpnam(name), "w");
  if (file == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  fprintf(file, "asdfasdf");
  fclose(file);
  file = fopen(name, "r");
  if (file == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  int c;
  int val = 0;

  while ((c = fgetc(file)) != EOF) {
    val++;
    putchar(c);
    if (val % 2 == 0) {
      ungetc(c + val, file);
    }
  }
  putchar('\n');
  fclose(file);
  unlink(name);
}
