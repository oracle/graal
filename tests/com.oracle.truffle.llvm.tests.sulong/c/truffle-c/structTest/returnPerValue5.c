struct test {
  char a;
  int b;
};

struct test function(struct test t, int iter) {
  t.a = 3 + t.a;
  t.b = 8 + t.b;
  if (iter == 0) {
    return t;
  } else {
    return function(t, iter - 1);
  }
}

int main() {
  struct test t = { 1, 2 };
  struct test r = function(t, 2);
  int sum = r.a + r.b;
  sum += function(t, 3).a;
  return sum + function(t, 4).a + function(t, 3).b;
}
