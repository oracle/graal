struct test {
  int a[3];
  char c;
  long d;
  int b;
};

int main() {
  struct test t = { .a = { 1 }, .d = 4 };
  return t.a[0]; // + t.a[1] + t.a[2] + t.c + t.d + t.b;
}
