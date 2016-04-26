int foo(int a[3], int counter) {
  if (counter == -1) {
    return 0;
  }
  return a[counter] + foo(a, counter - 1);
}

int main() {
  int a[3] = { 1, 2, 3 };
  return foo(a, 2);
}
