long m = 3;

int func(int n) {
  int arr1[(n + m) % 10];
  int arr2[(n + m) % 10];
  int i;
  for (i = 0; i < (n + m) % 10; i++) {
    arr1[i] = i;
    arr2[i] = i;
  }
  int sum = 0;
  for (i = 0; i < (n + m) % 10; i++) {
    sum += arr2[i];
    sum -= arr2[i];
  }
  return sum;
}

int main() {
  long n = 5;
  return func(10);
}
