#include <stdio.h>

int main() {
  char str[3];
  int retVal = snprintf(str, 3, "%d", 1231);
  printf("%s\n", str);
  return retVal;
}
