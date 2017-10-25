#include <stdio.h>
#include <string.h>

int main() {
  char dest[20];
  char *source = "asdfasdf";
  memmove(dest, source, strlen(source) + 1);
  puts(dest);
  memmove(&dest[1], dest, 4);
  puts(dest);
}
