int main() {
  int i = 0;
  int sum = 0;
  while (i++ < 100) {
    if (i % 2 == 0) {
      continue;
    }
    if (i >= 50) {
      break;
    }
    sum++;
  }
  return sum;
}
