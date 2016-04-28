#include <stdlib.h>

struct element {
  int val;
  struct element *next;
};

void insert(struct element *head, int val) {
  struct element *ptr = (struct element *)malloc(sizeof(struct element));
  struct element *cur = head;
  cur->next = ptr;
  ptr->val = val;
}

int main() {
  struct element *head = (struct element *)malloc(sizeof(struct element));
  head->val = 0;
  head->next = NULL;
  insert(head, 3);
  return head->next->val;
}
