#include <stdio.h>
#include <stdlib.h>

int main() {
  double val = strtod("-20.23 hello world", NULL);
  printf("val: %lf\n", val);
  val = strtod("+20.23 hello world", NULL);
  printf("val: %lf\n", val);
  return 0;
}
