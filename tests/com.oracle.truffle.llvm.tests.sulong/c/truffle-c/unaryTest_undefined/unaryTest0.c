int main() {
  int i = 1;
  // NOTE: unary semantics differ between
  // GCC and Clang
  // One unary per line hides these differences.
  int temp1 = i--;
  int temp2 = i++;
  return temp1 + temp2;
}
