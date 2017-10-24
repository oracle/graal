#include <stdio.h>
#include <stdlib.h>

int main() {
  char *str = "20.30500 hello world! 32";
  double val = strtod(str, NULL);
  printf("val: %lf\n", val);
  return 0;
}
