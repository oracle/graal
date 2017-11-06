#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

void test1(FILE *file, va_list args) { vfprintf(file, "%d %f %ld %c %s %% %x %X %i\n", args); }

void test2(FILE *file, ...) {
  va_list args;
  va_start(args, file);
  test1(file, args);
  va_end(args);
}

int main() {
  char name[200];
  FILE *file = fopen(tmpnam(name), "w");
  test2(file, 1, 2.3, 3L, 'a', "asdf", 123, 3242, -5);
  fclose(file);
  FILE *read = fopen(name, "r");
  char buf[20];
  while (fgets(buf, 20, read) != NULL) {
    printf("%s\n", buf);
  }
  fclose(read);
  unlink(name);
}
