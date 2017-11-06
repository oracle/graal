#include <stdlib.h>
#include <stdio.h>
#include <string.h>

int main() {
  const char *test = "asdfasdf";
  char *s = malloc(strlen(test) + 1);
  strcpy(s, test);
  char *new = strdup(s);
  free(s);
  puts(new);
  free(new);
}
