struct test {
  long a;
  int b;
  char c;
};

int func(struct test t, int arr[1]) {
  int sum = 0;
  sum += t.a;
  sum += t.c;
  sum += t.b;
  t.a = 0;
  t.b = 0;
  t.c = 0;
  sum += arr[0];
  return sum;
}

int main() {
  struct test t = { 5, 10, 20 };
  int arr[1] = { 1 };
  int sum = func(t, arr) - func(t, arr) + t.a + t.b + t.c + arr[0];
  return sum;
}
