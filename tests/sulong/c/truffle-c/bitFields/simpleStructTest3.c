struct test {
  int val : 1;
};

int main() {
  struct test t;
  t.val = 1; // -1
  long val = 1;
  return val + t.val;
}
