#define size 100

int main() {
  int arr[size] = { 1, 2, 0 };
  int i;
  int sum = 0;
  for (i = 0; i < size; i++) {
    sum -= arr[i];
    sum += 2 * arr[i];
  }
  return sum;
}
