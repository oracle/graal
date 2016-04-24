int main() {
  int val = ({
    int i;
    int sum = 0;
    for (i = 0; i < 10; i++) {
      sum += i;
    }
    sum++;
    sum;
  });
  return val + 2;
}
