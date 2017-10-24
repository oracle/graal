#include <stdio.h>
#include <string.h>

int main() {
  char str[50];
  memset(str, 'a', 10);
  str[10] = '\0';
  puts(str);

  return 0;
}
