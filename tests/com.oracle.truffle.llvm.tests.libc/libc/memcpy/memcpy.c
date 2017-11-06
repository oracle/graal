#include <stdio.h>
#include <string.h>

int main() {
  const char src[50] = "asdfasdf";
  char dest[50];

  printf("%s\n", src);
  memcpy(dest, src, strlen(src) + 1);
  printf("%s\n", dest);
}
