#include <stdio.h>
#include <stdlib.h>

int main() {
  char *str;
  if (NULL == (str = (char *)malloc(sizeof(char) * 10))) {
    abort();
  } else {
    free(str);
    return 0;
  }
}
