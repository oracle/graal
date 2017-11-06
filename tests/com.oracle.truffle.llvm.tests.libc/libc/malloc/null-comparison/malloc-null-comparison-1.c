#include <stdio.h>
#include <stdlib.h>

int main() {
  char *str;
  if ((str = (char *)malloc(sizeof(char) * 10)) == NULL) {
    abort();
  } else {
    free(str);
    return 0;
  }
}
