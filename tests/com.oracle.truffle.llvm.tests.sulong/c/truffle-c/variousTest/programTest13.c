int *func(int a, int *b) {
  static int result = 0;
  int i;
  for (i = 0; i < a; i++) {
    // NOTE: unary semantics differ between
    // GCC and Clang
    // One unary per line hides these differences.
    int temp = (*b)--;
    result += temp;
  }
  return &result;
}

int main() {
  int asdf = 3;
  int *ptr1 = func(4, &asdf);
  int *ptr2 = func(asdf, ptr1);
  return *func(5, ptr2) % 128;
}
