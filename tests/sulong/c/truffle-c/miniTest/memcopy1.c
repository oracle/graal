#include <stdio.h>
#include <string.h>

int main() {
  char src[50] = "asdfasdfyxdfasfasd";
  char dest[50];

  memcpy(dest, src, strlen(src) + 1);
  return strcmp(dest, src);
}
