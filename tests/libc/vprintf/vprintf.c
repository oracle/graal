#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>

void test1(va_list args) { vprintf("%d %f %ld %c %s %% %x %X %i\n", args); }

void test2(int dummy, ...) {
  va_list args;
  va_start(args, dummy);
  test1(args);
  va_end(args);
}

int main() { test2(-1, 1, 2.3, 3L, 'a', "asdf", 123, 3242, -5); }
