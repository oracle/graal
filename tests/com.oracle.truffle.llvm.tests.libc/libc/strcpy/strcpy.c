#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  char src[100];
  char dest[100];

  memset(dest, 0, sizeof(dest));
  strcpy(src, "hello world!");
  char *retDest = strcpy(dest, src);
  printf("%s\n", dest);
  if (retDest != dest) {
    abort();
  }
  return (0);
}
