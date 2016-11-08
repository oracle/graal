long function(int test[]) {

  if (test == &test[0]) {
    if (test == &test) {
      return 0;
    }
    return 1;
  }
  return 2;
}

int main() {
  int a[3] = { 0 };
  return function(a);
}
