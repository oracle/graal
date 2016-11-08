int main() {
  int sum = 0;
  int i = 10;
  do {
    sum += 1;
    i--;
    if (i == 0) {
      break;
    }
    if (1) {
      continue;
    }
    i++;
  } while (1);
  return sum;
}
