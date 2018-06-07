#include <stdio.h>

struct container {
  int (*callback)(int p1, int p2);
  int p1;
};

int (*get_callback_function())(int, int);
int call_callback(void *container, int p2);

int main(int argc, char **argv) {
  struct container c;
  c.callback = get_callback_function();
  c.p1 = 37;
  return call_callback(&c, 5);
}
