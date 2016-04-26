struct test {
  unsigned int val : 3;
};

int main() {
  struct test t;
  t.val = 8;
  return t.val;
}
