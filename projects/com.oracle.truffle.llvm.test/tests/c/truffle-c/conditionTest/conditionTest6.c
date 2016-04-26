int main() {
  int sum = 0;
  if (sum++ || sum++) {
    sum += 4;
  }
  if (0 && sum++) {
    sum += 8;
  }
  return sum;
}
