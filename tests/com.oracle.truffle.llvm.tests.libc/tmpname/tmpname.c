#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  char buffer[500];
  tmpnam(buffer);
  FILE *writeTo = fopen(buffer, "w");
  fputs("hello world!", writeTo);
  fclose(writeTo);
  FILE *readFrom = fopen(buffer, "r");
  fgets(buffer, 500, readFrom);
  fclose(readFrom);
  puts(buffer);
}
