struct test {
  char b[3];
  int a;
};

int main() {
  struct test t;
  t = ({ (struct test) { { 1, 2, 3 }, 1 }; });
  return t.b[0] + t.b[1] + t.b[2] + t.a;
}
