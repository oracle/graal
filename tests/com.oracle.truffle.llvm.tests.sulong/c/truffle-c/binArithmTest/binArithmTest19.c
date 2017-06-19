int k = 4;
int main() {
  static int i = 3;
  int j = 5;
  int sum1 = i + j * k % k++ / j;
  int sum2 = j++ * k + j >> 3;
  int sum3 = j >> k << i;
  return sum1 + sum2;
}
