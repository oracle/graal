#include <stdio.h>

int main() {
  char buffer[100];
  FILE *file = tmpfile();
  while (fgets(buffer, 100, file) != NULL) {
    printf("%s\n", buffer);
  }
}
