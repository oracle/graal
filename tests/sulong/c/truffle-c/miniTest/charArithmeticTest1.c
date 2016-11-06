int main() {
  unsigned char t = 12;
  signed char r = 1;
  char sum = t << r;
  sum %= r / r + t / t;
  sum *= r >> t;
  sum -= t % r;
  return sum;
}
