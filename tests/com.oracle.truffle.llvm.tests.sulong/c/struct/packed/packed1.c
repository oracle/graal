struct __attribute__((__packed__)) test {
  char a;
  int b;
  char c;
  char d;
  char e;
};

int main() {
  struct test t;
  t.a = 3;
  t.b = 2;
  t.c = 4;
  t.d = 5;
  t.e = 6;
  long result = *((long *)&t.a);
  return result % 256;
}
