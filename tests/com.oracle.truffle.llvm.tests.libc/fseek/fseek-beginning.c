#include <stdio.h>
#include <stdlib.h>

void print(FILE *file) {
  char buf[20];
  while (fgets(buf, 20, file) != NULL) {
    printf("%s\n", buf);
  }
}

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fputs("a asd a xdfasdf abn asdfasdf asdfdfaa", file);
  fclose(file);
  FILE *read = fopen(name, "r");
  if (fseek(read, 9, SEEK_SET) != 0) {
    abort();
  }
  print(read);
  if (fseek(read, 0, SEEK_SET) != 0) {
    abort();
  }
  print(read);
  if (fseek(read, 1000, SEEK_SET) != 0) {
    abort();
  }
  print(read);
}
