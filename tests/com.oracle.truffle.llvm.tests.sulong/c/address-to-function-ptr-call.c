void abort();

int add(int a, int b) { return a + b; }
int sub(int a, int b) { return a - b; }
int mul(int a, int b) { return a * b; }
int div(int a, int b) { return a / b; }

int rem(int a, int b) { return a % b; }

long *arr[5] = { (long *)&add, (long *)&sub, (long *)&mul, (long *)&div, (long *)&rem };

int main() {
  int i;
  int sum = 0;
  for (i = 0; i < 10000; i++) {
    int (*p)(int x, int y) = (int (*)(int x, int y))arr[i % 5];
    sum += p(i, 2);
  }
  if (sum != 44991000) {
    abort();
  }
}
