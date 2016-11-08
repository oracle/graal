struct test {
  int val : 1;
};

int main() {
  struct test t;
  t.val = 1; // -1
  return 1 + t.val;
}
