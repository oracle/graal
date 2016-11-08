int main() {
  int sum = 0;
  if (0 || 0 || 0 || -1) {
    sum += 1;
  }
  if (0 || 0 || 0 || 0 || sum) {
    sum += 2;
  }
  if (!!0) {
    sum += 4;
  }
  if (!sum) {
    sum += 8;
  }
  if (0 || 1 || 0 || 0) {
    sum += 16;
  }
  return sum;
}
