#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  char name[L_tmpnam];
  char buffer[500];
  tmpnam(name);
  FILE *writeTo = fopen(name, "w");
  if (writeTo == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  fputs("hello world!", writeTo);
  fclose(writeTo);
  FILE *readFrom = fopen(name, "r");
  if (readFrom == NULL) {
    printf("Failed to open file\n");
    abort();
  }
  fgets(buffer, 500, readFrom);
  fclose(readFrom);
  unlink(name);
  puts(buffer);
}
