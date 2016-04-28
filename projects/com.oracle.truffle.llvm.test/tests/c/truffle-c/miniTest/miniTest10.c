int foo(int a) { return a + 23; }

int main() {
  int i;
  int arr1[42];
  int arr2[42];
  for (i = 0; i < 42; i++) {
    arr1[i] = foo(i) + 29;
  }
  for (i = 0; i < 42; i++) {
    arr2[i] = arr1[i];
  }
  return arr2[22];
}
