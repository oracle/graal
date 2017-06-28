int main() {
  int result = 0;
  int a = 1;
  int b = 2;
  int c = 3;
  if (a <= b) {
    result = 1;
  }
  if (b > c) {
    result = result - 10000;
  }
  if (c >= a + b) {
    result = result + 10;
  }
  return result;
}
