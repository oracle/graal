union test {
  int a;
  double b;
};

int main() {
  union test t;
  t.b = 5.3;
  return t.a % 256;
}
