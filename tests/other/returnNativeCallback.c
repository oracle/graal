#include <stdio.h>

int (*get_callback_function())(int, int);

int main(int argc, char **argv) {
  int (*fn)(int, int);
  fn = get_callback_function();
  return fn(7, 3);
}
