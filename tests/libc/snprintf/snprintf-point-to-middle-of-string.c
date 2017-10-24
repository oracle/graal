#include <stdio.h>
#include <string.h>

int main() {
  char str[100];
  memset(str, 'A', 50);
  int retVal = snprintf(&str[30], 100, "%s %d", "this is the number: ", 1231);
  printf("%s\n", str);
  return retVal;
}
