struct test {
  char b[3];
  int a;
};

int main() {
  struct test t;
  t = ({
    struct test t;
    t.b[0] = 4;
    t.b[1] = 32;
    t.b[2] = 43;
    t.a = -10 ? 32 : 4;
    t;
  });
  return t.b[0] + t.b[1] + t.b[2] + t.a;
}
