#include <stdio.h>

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fclose(file);
  freopen(name, "r", stdin);
  char buf[200];
  while (gets(buf) != NULL) {
    printf("%s\n", buf);
  }
}
