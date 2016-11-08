struct test {
  unsigned int val1 : 1;
  unsigned int val2 : 1;
};

int main() {
  struct test t = { 1, 1 };
  return t.val2 + t.val1;
}
