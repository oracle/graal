int main() {
  int sum = 0;
  sum += !0 ? 1 : 2;
  sum += sum ? 4 : 8;
  sum += (5 && 3) ? 16 : 32;
  return sum;
}
