void bubbleSort(int numbers[], int array_size) {
  int i, j, temp;

  for (i = (array_size - 1); i > 0; i--) {
    for (j = 1; j <= i; j++) {
      if (numbers[j - 1] > numbers[j]) {
        temp = numbers[j - 1];
        numbers[j - 1] = numbers[j];
        numbers[j] = temp;
      }
    }
  }
}

int main() {
  int a[20] = { 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1 };

  bubbleSort(a, 20);

  return a[3];
}
