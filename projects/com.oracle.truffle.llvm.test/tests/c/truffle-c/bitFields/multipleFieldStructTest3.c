struct test {
  unsigned int val1 : 3;
  unsigned int val2 : 3;
};

int main() {
  struct test t;
  t.val2 = 0;
  t.val1 = 8; // -1
  int val = t.val1 + t.val2;
  return val;
}
