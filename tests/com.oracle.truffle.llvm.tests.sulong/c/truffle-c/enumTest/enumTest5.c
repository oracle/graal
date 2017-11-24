enum E1 { B1 = 3, B2 /* = 4 */, B3 = 5 };

int func(enum E1 val) {
  static int sum = 0;
  sum += (int)val;
  return sum;
}

int main() {
  enum E1 e;
  e = B1;
  func(e);
  e = B2;
  func(e);
  e = B3;
  return func(e);
}
