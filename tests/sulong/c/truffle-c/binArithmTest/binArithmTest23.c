int main() {
  int i = 0, j = 10;
  int sum = 0;
  while (i++ < --j) {
    sum++;
  }
  i = 0;
  j = 10;
  while (i++ < j--) {
    sum++;
  }
  return sum;
}
