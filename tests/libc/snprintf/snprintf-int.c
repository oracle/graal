#include <stdio.h>

int main() {
  char str[100];
  int retVal = snprintf(str, 100, "%d", 1231);
  printf("%s\n", str);
  return retVal;
}
