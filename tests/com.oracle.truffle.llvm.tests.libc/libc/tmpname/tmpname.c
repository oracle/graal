#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  char name[100];
  char buffer[500];
  tmpnam(name);
  FILE *writeTo = fopen(name, "w");
  fputs("hello world!", writeTo);
  fclose(writeTo);
  FILE *readFrom = fopen(name, "r");
  fgets(buffer, 500, readFrom);
  fclose(readFrom);
  unlink(name);
  puts(buffer);
}
