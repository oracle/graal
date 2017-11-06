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

  writeableFile = fopen("sulong_test_file", "a");
  if (writeableFile == NULL) {
    printf("error opening file!\n");
    exit(2);
  }
  fprintf(writeableFile, "asdfasdfasdf");
  fclose(writeableFile);

  FILE *readableFile = fopen("sulong_test_file", "r");
  if (readableFile == NULL) {
    printf("error opening file!\n");
    exit(3);
  }
  char buff[1000];
  while (fgets(buff, 1000, readableFile) != NULL) {
    printf("%s\n", &buff[0]);
  }
  if (remove("sulong_test_file")) {
    printf("error removing file!\n");
    exit(4);
  }
}
