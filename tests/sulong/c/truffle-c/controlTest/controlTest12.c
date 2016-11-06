int main() {
  int sum = 0;
  int i, n = 10;

  i = 0;
  for (;;) {
    if (i >= n) {
      break;
    }
    sum += i;
    i++;
  }
  for (i = 0;; i++) {
    if (i == n / 2) {
      break;
    }
    sum -= 1;
  }
  i = 0;
  for (; i < n / 3;) {
    sum -= i;
    i++;
  }
  return sum;
}
