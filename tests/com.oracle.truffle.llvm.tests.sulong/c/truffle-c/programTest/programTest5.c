int main() {
  int a = 15975;
  int b = 12684;
  while (1) {
    a = a % b;
    if (a == 0)
      return b;
    b = b % a;
    if (b == 0)
      return a;
  }
}
