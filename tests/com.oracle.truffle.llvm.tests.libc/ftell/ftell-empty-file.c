#include <stdio.h>
#include <stdlib.h>

int main() {
  FILE *fp = tmpfile();
  if (ftell(fp) != 0) {
    abort();
  }
}
