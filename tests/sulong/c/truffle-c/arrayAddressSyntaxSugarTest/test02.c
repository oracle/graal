long function(int test[]) { return test; }

int main() {
  int a[3] = { 0 };
  return function(a) == a;
}
