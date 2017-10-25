#include <stdio.h>
#include <string.h>

int main() {
  char src[1] = { 'a' };
  char dest[1] = { 'b' };
  memcpy(&dest, &src, 0);
  printf("%c %c\n", src[0], dest[0]);
}
