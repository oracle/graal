int func(int upTo) {
  static int arr[10] = { 0 };
  int sum = 0, i = 0;
  for (; i < upTo; i++) {
    sum = arr[i];
  }
  arr[upTo] = sum;
  return sum;
}

int main() {
  int i;
  int s;
  for (i = 0; i < 10; i++) {
    s = func(i);
  }
  return s;
}
