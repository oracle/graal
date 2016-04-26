int main() {
  int arr1[10] = { 10 };       // 10, 10, 10, ...
  int arr2[10] = { 1, 2 };     // 1, 2, 0, 0, ...
  static int arr3[10] = { 0 }; // 0, 0, 0, 0
  int i = 0;
  int sum = 0;
  while (i < 10) {
    sum += arr1[i];
    sum += arr2[i];
    sum += arr3[i++];
  }
  return sum;
}
