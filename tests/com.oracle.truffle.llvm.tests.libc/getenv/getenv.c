#include <stdio.h>
#include <stdlib.h>

int main() {
  printf("PATH: %s\n", getenv("PATH"));
  printf("HOME: %s\n", getenv("HOME"));
  printf("ROOT: %s\n", getenv("ROOT"));
}
