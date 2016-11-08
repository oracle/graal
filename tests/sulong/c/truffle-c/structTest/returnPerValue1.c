struct test {
  char a;
  int b;
};

struct test function() {
  struct test t;
  t.a = 3;
  t.b = 8;
  return t;
}

int main() {
  struct test t;
  t = function();
  return t.a + t.b;
}
