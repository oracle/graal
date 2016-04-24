int main() {
  int a = 0;
  switch (0) {
  case 0:
    a += 1;
  case 1:
    a += 2;
  case 2:
    a += 3;
  default:
    a += 4;
  }
  return a;
}
