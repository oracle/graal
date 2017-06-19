struct test {
  int a;
};

int func() {
  static struct test t = { 0 };
  return t.a++;
}

int main() {
  int sum = 0;
  sum += func();
  sum += func();
  sum += func();
  return sum;
}
