struct test {
  char a[3];
};

int func(struct test t) {
  int sum = 0;
  sum += t.a[0];
  sum += t.a[1];
  sum += t.a[2];
  t.a[0] = 0;
  t.a[1] = 0;
  t.a[2] = 0;
  return sum;
}

int main() {
  struct test t = { { 1, 2, 3 } };
  int ret = func(t) + func(t);
  return ret + t.a[0] + t.a[1] + t.a[2];
}
