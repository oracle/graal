#include <stdio.h>
#include <stdlib.h>

int main() {
  char *str = "20.3012308 hello world! 32";
  char *ptr;

  double val;
  do {
    val = strtod(str, &ptr);
    printf("val: %lf\n", val);
    printf("string: %s\n", ptr);
    str = ptr;
  } while (val != 0);
  return 0;
}
