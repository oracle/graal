struct test {
  long val : 1;
};

int main() {
  struct test t;
  t.val = 1; // -1
  long val = t.val;
  return val + 1;
}
