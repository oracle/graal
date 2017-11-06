#include <stdio.h>

int main() {
  char str[100];
  int retVal = snprintf(str, 100, "%s %d", "this is the number: ", 1231);
  printf("%s\n", str);
  return retVal;
}
