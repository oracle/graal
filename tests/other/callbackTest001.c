#include <stdio.h>

void *create_container(int (*callback)(int p1, int p2), int p1);
int call_callback(void *container, int p2);

int callback(int p1, int p2) {
  return p1 + p2;
}

int main(int argc, char** argv) {
  void *container = create_container(callback, 14);
  int x = call_callback(container, 2);
  return x;
}
