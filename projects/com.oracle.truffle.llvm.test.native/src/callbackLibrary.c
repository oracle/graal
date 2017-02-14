#include <stdlib.h>

struct container {
  int (*callback)(int p1, int p2);
  int p1;
};

void *create_container(int (*callback)(int p1, int p2), int p1) {
  struct container *c = malloc(sizeof(struct container));
  c->callback = callback;
  c->p1 = p1;
  return c;
}

int call_callback(void *container, int p2) {
  struct container *c = (struct container *) container;
  return c->callback(c->p1, p2);
}
