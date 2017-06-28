struct t {
  int val;
  struct t *ptr;
};

const int size = 10000;

int main() {
  struct t test;
  test.val = size;
  test.ptr = &test;
  int sum = 0;
  while (test.val) {
    sum++;
    test.val--;
    test.ptr = test.ptr->ptr;
  }
  return sum / size;
}
