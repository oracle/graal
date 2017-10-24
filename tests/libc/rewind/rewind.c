#include <stdio.h>
#include <stdlib.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fputs("a asd a xdfasdf abn asdfasdf asdfdf\n", file);
  fclose(file);
  FILE *read = fopen(name, "r");
  char buf[20];
  while (fgets(buf, 20, read) != NULL) {
    printf("%s\n", buf);
  }
  rewind(read);
  while (fgets(buf, 20, read) != NULL) {
    printf("%s\n", buf);
  }
  printf("%ld\n", ftell(read));
}
