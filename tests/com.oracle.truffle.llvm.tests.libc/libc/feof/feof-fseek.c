#include <stdio.h>
#include <stdlib.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fputs("a asd a xdfasdf abn asdfasdf asdfdfaa", file);
  fclose(file);
  FILE *read = fopen(name, "r");
  fseek(read, 0, SEEK_END);
  while (1) {
    int c = fgetc(read);
    if (feof(read)) {
      break;
    }
    putchar(c);
  }
  putchar('\n');
}
