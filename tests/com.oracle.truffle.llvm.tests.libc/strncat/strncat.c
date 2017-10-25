#include <stdio.h>
#include <string.h>

int main() {
  char src[50];
  char dest[50];

  strcpy(dest, "asdfsdf");

  strncat(dest, "yxcvyser", 5);
  strncat(dest, "", 5);
  strncat(dest, "123", 5);

  printf("result: %s\n", dest);
}
