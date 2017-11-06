#include <stdio.h>
#include <stdlib.h>

void print(FILE *file, int size) {
  char buf[size];
  while (fgets(buf, size, file) != NULL) {
    printf("%s\n", buf);
  }
}

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fputs("a asd a xdfasdf abn asdfasdf asdfdfaa", file);
  fclose(file);
  FILE *read = fopen(name, "r");

  if (fseek(read, -5, SEEK_END) != 0) {
    abort();
  }
  print(read, 5);

  if (fseek(read, 0, SEEK_CUR) != 0) {
    abort();
  }
  print(read, 5);
  printf("%ld\n", ftell(read));
  fclose(read);
  unlink(name);
}
