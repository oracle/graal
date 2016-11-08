int arr[2] = { 1, 2 };

int *foo() { return arr; }

int main() {
  int *a = foo();
  return a[0] + a[1];
}
