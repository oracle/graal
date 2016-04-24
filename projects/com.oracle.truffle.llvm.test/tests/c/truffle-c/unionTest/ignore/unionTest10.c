union test {
  int a[2];
  char c[8];
  double d;
};

int main() {
  union test a;
  int i = 0;
  for (i = 0; i < 8; i++) {
    a.c[i] = i + 1;
  }
  return a.a[0] + a.a[1];
}
