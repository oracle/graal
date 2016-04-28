int main() {
  int i = 0;
  int result = 10;
  switch (i) {
  case 0:
    result = 5;
    break;
  case 1:
    result = 8;
    break;
  case 2:
    result = 9;
    break;
  case 3:
    result = 2;
    break;
  default:
    result = 13;
  }
  return result;
}
