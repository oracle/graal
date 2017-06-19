int b = 8;

int *test() {
  static int a = 3;
  a++;
  return &a;
}

int main() {
  int c = 4;
  return *test() + 8 + c;
}
