struct test {
  union field {
    int b;
    long c;
  } a;
  char chars[4];
};

int main() {
  struct test t = { .a.c = -1 };
  return 1 + t.a.b + t.chars[0] + t.chars[1] + t.chars[2] + t.chars[3];
}
