int test() {
  static int a = 3;
  return a++;
}

int main() { return test() + test() + test(); }
