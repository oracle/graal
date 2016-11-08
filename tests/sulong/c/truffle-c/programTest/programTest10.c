#define n 10

int accumulated_sum(int incr) {
  static int sum = 0;
  sum += incr;
  return sum;
}

int main() {
  int i = 0;
  for (; i < n; i++) {
    accumulated_sum(i);
  }
  return accumulated_sum(0);
}
