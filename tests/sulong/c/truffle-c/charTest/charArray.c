char arr[4] = { 'a', 'b', 2, 3 };

int main() {
  int i;
  char sum = 0;
  for (i = 0; i < 4; i++) {
    sum = sum + arr[i];
  }
  return 100 + sum;
}
