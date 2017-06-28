int func(int a, int *b) {
  int result = 0;
  int i;
  for (i = 0; i < a; i++) {
    // NOTE: unary semantics differ between
    // GCC and Clang
    // One unary per line hides these differences.
    int temp = (*b);
    *b = *b - 1;
    result += temp;
  }
  return result;
}

int main() {
  int asdf = 3;
  // NOTE: unary semantics differ between
  // GCC and Clang
  // One unary per line hides these differences.
  int t1 = asdf;
  int t2 = func(4, &asdf);
  int t3 = asdf;
  return t1 + t2 + t3;
}
