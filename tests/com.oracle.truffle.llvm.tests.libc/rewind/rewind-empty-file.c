#include <stdio.h>

int main() {
  int c;
  FILE *fp = tmpfile();
  while ((c = fgetc(fp)) != EOF) {
    putchar(c);
  }
  rewind(fp);
  while ((c = fgetc(fp)) != EOF) {
    putchar(c);
  }
}
