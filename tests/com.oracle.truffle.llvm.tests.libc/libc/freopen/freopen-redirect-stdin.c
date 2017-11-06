#include <stdio.h>

int main() {
  freopen(__FILE__, "r", stdin);
  char buf[200];
  while (gets(buf) == buf) {
    printf("%s\n", buf);
  }
}
