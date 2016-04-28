int main() {
  int sum = 0;
  if (0 && sum++) {
    sum += 2;
  }
  if (1 && 2 && 3 && 4 && 5) {
    sum += 4;
  }
  if (5 && 4 && 3 && 0) {
    sum += 8;
  }
  if (!sum && !sum) {
    sum += 16;
  }
  return sum;
}
