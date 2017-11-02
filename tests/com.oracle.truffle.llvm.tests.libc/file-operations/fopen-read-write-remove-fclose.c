#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
int main() {
  FILE *writeableFile = fopen("sulong_test_file", "w");
  if (writeableFile == NULL) {
    printf("error opening file!\n");
    exit(1);
  }
  const char *text = "hello world!";
  fprintf(writeableFile, "write this to the writeableFile: %s\n", text);
  if (fclose(writeableFile) == EOF) {
    exit(4);
  }
  FILE *readableFile = fopen("sulong_test_file", "r");
  if (readableFile == NULL) {
    printf("error opening file!\n");
    exit(2);
  }
  char buff[1000];
  fgets(buff, 1000, readableFile);
  if (fclose(readableFile) == EOF) {
    exit(4);
  }
  fputs(buff, stdout);

  if (remove("sulong_test_file")) {
    printf("error removing file!\n");
    exit(3);
  }
}
