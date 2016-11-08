struct test {
  char a;
  int b;
};

struct test function() {
  struct test t;
  t.a = 41;
  t.b = 8;
  return t;
}

int main() {
  struct test t;
  return function().a;
}
