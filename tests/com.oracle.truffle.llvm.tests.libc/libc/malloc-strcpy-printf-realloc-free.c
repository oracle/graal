#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main() {
  char *str = (char *)malloc(15);
  strcpy(str, "hello world");
  printf("%s\n", str);
  str = (char *)realloc(str, 25);
  printf("%s\n", str);

  free(str);

  return (0);
}
