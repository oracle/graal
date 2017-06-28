int main() {
  static int array_size = 1000;
  int arr[1000] = { 0 };
  int i;
  for (i = 0; i < array_size; i++) {
    arr[i] = i;
  }
  int sum = arr[50] + arr[array_size - 1];
  return (sum + i) % 128;
}
