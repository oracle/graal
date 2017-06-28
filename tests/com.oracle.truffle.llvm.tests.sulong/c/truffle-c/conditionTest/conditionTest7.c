
int main() {
  int a = 3;
  int b = !!4;
  int sum = a == b;
  sum += 0 || b;
  sum += a || b;
  return sum;
}
