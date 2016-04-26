int main() {
  int int_bits = 8 * sizeof(int);
  int value = 1 << int_bits - 1;
  return 2 * value + 1;
}
