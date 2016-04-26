int main() {
  int shift = sizeof(int) * 8 - 1;
  return (-1 >> shift) + 2;
}
