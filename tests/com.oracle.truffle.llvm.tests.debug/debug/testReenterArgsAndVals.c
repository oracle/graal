int main(int argc, char **argv) {
  int i = 10;
  return fnc(i = i + 1, 20);
}
int fnc(int n, int m) {
  int x = n + m;
  n = m - n;
  m = m / 2;
  x = x + n * m;
  return x;
}
