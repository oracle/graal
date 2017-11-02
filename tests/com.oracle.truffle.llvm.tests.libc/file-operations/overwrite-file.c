#include <stdio.h>
#include <stdlib.h>

int main() {
  FILE *writeableFile = fopen("sulong_test_file", "w");
  if (writeableFile == NULL) {
    printf("error opening file!\n");
    exit(1);
  }
  const char *text = "hello world!";
  fprintf(writeableFile, "write this to the writeableFile: %s\n", text);
  fclose(writeableFile);

  writeableFile = fopen("sulong_test_file", "w");
  if (writeableFile == NULL) {
    printf("error opening file!\n");
    exit(1);
  }
  char buff[1000];
  if (fgets(buff, 1000, writeableFile) != NULL) {
    printf("error!");
  }
  if (remove("sulong_test_file")) {
    printf("error removing file!\n");
    exit(3);
  }
}
