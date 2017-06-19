long m = 3;

int func(int n) {
  int arr[(n + m) % 10];
  int i;
  for (i = 0; i < (n + m) % 10; i++) {
    arr[i] = i;
  }
  int sum = 0;
  for (i = 0; i < (n + m) % 10; i++) {
    sum += arr[i];
  }
  return sum;
}

int main() {
  long n = 5;
  return func(10);
}
