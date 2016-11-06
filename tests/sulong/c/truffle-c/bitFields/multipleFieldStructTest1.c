struct test {
  unsigned int val1 : 1;
  unsigned int val2 : 1;
};

int main() {
  struct test t;
  t.val2 = 0;
  t.val1 = 1; // -1
  int val = t.val1 + t.val2;
  return val;
}
