int main() {
  int i = 0;
  int sum = 0;
  for (i = 0; i < 10; i++) {
    if (i < 5) {
      continue;
    }
    sum++;
  }
  return sum;
}
