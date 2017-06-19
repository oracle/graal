void func(int *ptr) { *ptr = 7; }

int main() {
  static int var;
  func(&var);
  return var;
}
