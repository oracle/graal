int main() {
  int i;
  int sum = 0;
  for (i = 0; i < 10000; i++) {
    sum += func(1000);
  }
  if (sum != 10000) {
    abort();
  }
  return 0;
}

int func(n) {
  long arr[n];
  return 1;
}
