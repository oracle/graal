struct test {
  int arr[3][2];
};

int main() {
  struct test at = { { { 1, 2 }, { 3, 4 }, { 5, 6 } } };
  struct test t = at;
  int sum = 0;
  int i, j;
  for (i = 0; i < 2; i++) {
    for (j = 0; j < 3; j++) {
      sum += t.arr[i][j];
      sum *= 2;
    }
  }
  return sum;
}
