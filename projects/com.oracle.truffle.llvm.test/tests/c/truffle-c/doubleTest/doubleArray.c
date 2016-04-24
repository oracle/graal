double arr[] = { 1.0, 2.0, 3.0, 4.0 };

int main() {
  int i;
  double sum = 0;
  for (i = 0; i < 4; i++) {
    sum = sum + arr[i];
  }
  return (int)sum;
}
