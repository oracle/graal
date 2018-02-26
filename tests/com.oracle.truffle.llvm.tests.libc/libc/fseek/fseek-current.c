#include <stdio.h>
#include <stdlib.h>

void print(FILE *file, int size) {
  char buf[size];
  fgets(buf, size, file);
  printf("%s\n", buf);
}

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

  if (fseek(read, 9, SEEK_CUR) != 0) {
    abort();
  }
  print(read, 5);

  if (fseek(read, 3, SEEK_CUR) != 0) {
    abort();
  }
  print(read, 7);

  if (fseek(read, -4, SEEK_CUR) != 0) {
    abort();
  }
  print(read, 10);
  fclose(read);
  unlink(name);
}
