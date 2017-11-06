#include <stdio.h>

int main() {
  int oldStdin = dup(0);
  freopen(__FILE__, "r", stdin);
  char buf[200];
  while (gets(buf) == buf) {
    printf("%s\n", buf);
  }
  fclose(stdin);
  dup2(oldStdin, 0);
  close(oldStdin);
  stdin = fdopen(0, "r");
}
