#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  char src[100];
  char dest[100];

  memset(dest, 0, sizeof(dest));
  strcpy(src, "\0");
  char *retDest = strcpy(dest, src);
  printf("%s\n", dest);
  if ((void *)retDest != (void *)&dest) {
    abort();
  }
  return (0);
}
