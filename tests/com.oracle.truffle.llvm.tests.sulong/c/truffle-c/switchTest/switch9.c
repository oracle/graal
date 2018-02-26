int main() {
  short test = 1;
  switch (test) {
  case 10L:
    return 1;
  case (char)3:
    return 2;
  case (long)1:
    return 3;
  default:
    return 4;
  }
}
