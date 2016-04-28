struct test {
  int val;
  char c;
  int val2[3];
};

int main() {
  struct test t = { 1, 'a', { 1, 2, 3 } };
  return t.val + t.c - t.val2[0] - t.val2[1] - t.val2[2];
}
