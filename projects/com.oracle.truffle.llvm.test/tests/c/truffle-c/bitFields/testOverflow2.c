struct test {
  unsigned int val : 3;
};

int main() {
  struct test t;
  t.val = 9;
  return t.val;
}
