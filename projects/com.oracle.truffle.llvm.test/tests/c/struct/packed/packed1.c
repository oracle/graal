struct __attribute__((__packed__)) test {
  char a;
  int b;
  char c;
};

int main() {
  struct test t;
  t.a = 3;
  t.b = 2;
  long result = *((long *)&t.a);
  return result % 256;
}
