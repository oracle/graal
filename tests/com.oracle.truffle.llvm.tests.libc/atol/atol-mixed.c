#include <stdlib.h>
#include <stdio.h>

int main() {
  printf("%ld\n", atol("123a"));
  printf("%ld\n", atol("a123"));
  printf("%ld\n", atol("a123a"));
}
