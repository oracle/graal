#include <string.h>
#include <stdio.h>

int main() {

  char str1[] = "aaXaa";
  char str2[] = "aaYaa";

  printf("%d\n", strncmp(str1, str2, 2));
  printf("%d\n", strncmp(str2, str1, 2));

  printf("%d\n", strncmp(str1, str2, 5));
  printf("%d\n", strncmp(str2, str1, 5));
}
