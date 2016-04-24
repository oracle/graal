union test {
  int a[2];
  char c[8];
  double d;
};

int main() {
  union test a;

  a.d = 3.1415;

  return a.a[0] + a.a[1];
}
