int main() {
  int a = 1;
  switch (1) {
  case 0:
    a += a + 3;
    a += 1;
    return 0;
  case 1:
    a = 43;
    a += 1;
    a += 2;
    break;
  case 2:
    a = 4;
    break;
  case 3:
    a = 5;
    break;
  default:
    return 4;
  }
  return a;
}
