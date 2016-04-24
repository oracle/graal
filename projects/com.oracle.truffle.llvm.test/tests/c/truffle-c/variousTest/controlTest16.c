int main() {
  int sum = 0;
  int i = 3;
  switch (i) {
  case 2:
    sum += 2;
  case 3:
    sum += 3;
  case 4:
    sum += 4;
  case 5:
    sum += 5;
  }
  return sum;
}
