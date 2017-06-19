struct test {
  int a[1];
};

int func(struct test t) {
  int sum = 0;
  sum += t.a[0];
  t.a[0] = 0;
  return sum;
}

int main() {
  struct test t = { { 1 } };
  int ret = func(t) + func(t);
  return ret + t.a[0];
}
