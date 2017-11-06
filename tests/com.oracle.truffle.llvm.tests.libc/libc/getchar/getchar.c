#include <stdio.h>
#include <stdlib.h>

int main() {
  char c;
  int oldStdin = dup(0);
  FILE *file = freopen(__FILE__, "r", stdin);
  while ((c = getchar()) != EOF) {
    putchar(c);
  }
  fclose(stdin);
  dup2(oldStdin, 0);
  close(oldStdin);
  stdin = fdopen(0, "r");
}
