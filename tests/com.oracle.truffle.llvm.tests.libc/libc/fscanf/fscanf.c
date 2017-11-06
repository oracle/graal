#include <stdio.h>
#include <stdlib.h>

int main() {
  FILE *writeableFile = fopen("sulong_test_file", "w");
  if (writeableFile == NULL) {
    printf("error opening file!\n");
    exit(1);
  }
  fputs("asdfasdf aa 543 -12312 xcvb", writeableFile);
  fclose(writeableFile);
  FILE *readableFile = fopen("sulong_test_file", "r");
  if (readableFile == NULL) {
    printf("error opening file!\n");
    exit(2);
  }
  char buf[100];
  char c1, c2;
  int i1;
  if (fscanf(readableFile, "%s %c%c %d xcvb", buf, &c1, &c2, &i1) != 4) {
    abort();
  }
  fclose(readableFile);
  printf("%s %c %c %d\n", buf, c1, c2, i1);
}
