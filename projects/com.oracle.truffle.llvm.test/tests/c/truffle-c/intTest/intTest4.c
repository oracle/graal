int main() {
  int int_bits = 8 * sizeof(int);
  int num = 0;
  int i = 0;
  while (i < int_bits) {
    num = num << 1 | 1;
    i++;
  }
  return -1 == num;
}
