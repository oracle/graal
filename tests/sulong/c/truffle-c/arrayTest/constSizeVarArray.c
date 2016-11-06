int main() {
  int n = 5;
  int x[3 * n];
  int i;
  for (i = 0; i < 15; i++) {
    x[i] = i;
  }
  int sum = 0;
  for (i = 0; i < 15; i++) {
    sum += x[i];
  }
  return sum;
}
