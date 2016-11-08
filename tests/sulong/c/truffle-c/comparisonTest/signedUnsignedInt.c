int main() {
  signed a = -1;
  unsigned b = -1;
  int sum = 0;
  sum += a <= b;
  sum *= 2;
  sum += a < b;
  sum *= 2;
  sum += a > b;
  sum *= 2;
  sum += a >= b;
  sum *= 2;
  sum += a == b;
  sum *= 2;
  sum += a != b;
  return sum;
}
