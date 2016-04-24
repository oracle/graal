int main() {
  int sum = 0;
  int i, j, k;
  for (i = 0; i < 10; i++) {
    for (j = 0; j < 10; j++) {
      k = 0;
      while (k < 10) {
        sum += ((i - 2 + j) % (k == 0 ? 1 : k));
        k++;
        if ((k + i) == 8) {
          break;
        }
      }
      if ((i + j) % 3 == 2) {
        break;
      }
    }
  }
  return sum % 100;
}
