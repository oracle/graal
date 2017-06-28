int main() {
  int sum = 0;
  if (0 || 3 && 0 && sum++) {
    sum += 2;
  }
  if (0 || 1 && 0) {
    sum += 4;
  }
  if (1 && 0 || 4 && 6) {
    sum += 8;
  }

  if (1 && 2 && 3 || 5) {
    sum += 16;
  }
  return sum;
}
