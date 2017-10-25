#include <string.h>
#include <stdio.h>
#include <stdlib.h>

char *str = "Hello world!\n";

int main(void) {
  int length = strlen(str) + 1;
  char *test = malloc(length * sizeof(char));
  memcpy(test, str, length);
  test[1] = 'a';
  memcpy(&(test[6]), "Welt!", strlen("Welt!"));
  fputs(test, stdout);
  return 0;
}
