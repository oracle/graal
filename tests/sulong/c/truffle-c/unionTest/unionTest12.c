union test {
  int a[2];
  char c[8];
  double d;
};

int main() {
  union test a;

  a.d = 3.1415;

  return 10 + a.c[0] + a.c[1] + a.c[2] + a.c[3] + a.c[4] + a.c[5] + a.c[6] + a.c[7];
}
