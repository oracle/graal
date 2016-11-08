int sum(const int *arr, int begin, const int end) {
  int sum = 0;
  while (begin <= end) {
    sum += arr[begin++];
  }
  return sum;
}

int main() {
  int arr[10] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
  return sum(arr, 2, 9);
}
