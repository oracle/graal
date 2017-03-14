#include <stdio.h>

void *create_container(int (*callback)(int p1, int p2), int p1);
int call_callback(void *container, int p2);

int main(int argc, char** argv) {
  void *container = create_container(0, 14);
  return 14;
}
