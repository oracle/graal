int main() {
  int n = sizeof(int) * 8 * 2;
  int i = 1;
  int max_value_plus_one = 1;
  for (i = 0; i < n - 1; i++) {
    max_value_plus_one = max_value_plus_one * 2; // 2^(n-1)
  }
  return max_value_plus_one;
}
