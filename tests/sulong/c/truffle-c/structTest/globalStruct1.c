struct test {
  int a;
};

struct test t = { 0 };

int func() { return t.a++; }

int main() {
  int sum = 0;
  sum += func();
  sum += func();
  return sum;
}
