#include <stdio.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fprintf(file, "asdfasdf");
  fclose(file);
  file = fopen(name, "r");
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
}
