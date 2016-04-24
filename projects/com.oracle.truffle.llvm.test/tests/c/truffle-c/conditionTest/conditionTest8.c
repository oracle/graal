int main() {
  int sum = 0;
  int i = 1;
  if (--i) {
    sum += 1;
  }
  if (i++ || i++) {
    sum += 2;
  }
  return sum;
}
