#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  FILE *tmp = tmpfile();
  char buffer[100];
  memset(buffer, 0, 100);
  if (fgets(buffer, 100, tmp) != NULL) {
    abort();
  }
  puts(buffer);
}
