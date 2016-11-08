int foo(int a, int b, int c, int d, int e, int f) {
  if (a == 5) {
    if (e == 5) {
      return 0;
    }
  } else {
    return 1;
  }
}

int main() {
  int a, b, c, d, e, f, g, h, i;
  a = b = c = d = e = f = 5;
  return foo(a, b, c, d, e, f);
}
