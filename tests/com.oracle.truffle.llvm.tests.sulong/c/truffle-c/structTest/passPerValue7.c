struct test {
  char a;
  int b;
};

int function(struct test t, int iter) {
  t.a = 3 + t.a;
  t.b = 8 + t.b;
  if (iter == 0) {
    return t.a + t.b;
  } else {
    return function(t, iter - 1);
  }
}

int main() {
  struct test t = { 1, 2 };
  return (function((struct test){ 1, 2 }, 20) + function(t, 10)) % 256;
}
