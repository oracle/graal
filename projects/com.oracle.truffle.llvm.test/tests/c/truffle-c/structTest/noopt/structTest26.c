#include <stdlib.h>
#include <stdio.h>

struct element {
  int val;
  struct element *next;
};

void insert(struct element *head, int val) {
  struct element *ptr = (struct element *)malloc(sizeof(struct element));
  ptr->next = 0;
  ptr->next = NULL;
  struct element *cur = head;
  while (cur->next != NULL) {
    cur = cur->next;
  }
  cur->next = ptr;
  cur->val = val;
}

int sum(struct element *head) {
  int sum = head->val;
  while (head->next != NULL) {
    sum += head->val;
    head = head->next;
  }
  return sum;
}

int main() {
  struct element *head = (struct element *)malloc(sizeof(struct element));
  head->val = 0;
  head->next = NULL;
  insert(head, 3);
  insert(head, 7);
  insert(head, 8);
  insert(head, -2);
  insert(head, -4);
  insert(head, 13);
  insert(head, 2);
  int result = sum(head);
  return result;
}
