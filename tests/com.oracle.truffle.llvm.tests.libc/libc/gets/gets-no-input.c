#include <stdio.h>

int main() {
  int oldStdin = dup(0);
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  fclose(file);
  freopen(name, "r", stdin);
  char buf[200];
  while (gets(buf) != NULL) {
    printf("%s\n", buf);
  }

  fclose(stdin);
  unlink(name);
  dup2(oldStdin, 0);
  stdin = fdopen(0, "r");
  close(oldStdin);
}
