struct list {
  int value;
  struct list *next;
};

int main() {
  struct list n1, n2, n3;
  int i;

  n1.value = 100;
  n2.value = 200;
  n3.value = 300;
  n1.next = &n2;
  n2.next = &n3;
  i = n1.next->value;
  return i;
}
