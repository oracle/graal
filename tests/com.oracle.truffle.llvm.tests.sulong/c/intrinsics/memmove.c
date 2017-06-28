#include <stdlib.h>

void move(char *target, char *source, int length) { __builtin_memmove(target, source, length); }

int main(int argc, char *argv[]) {
  char *source = "asdf";
  char target[__builtin_strlen(source) + 1];
  move(target, source, __builtin_strlen(source) + 1);
  if (__builtin_strcmp(target, source) != 0) {
    abort();
  } else {
    exit(0);
  }
}
