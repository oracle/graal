int d = 6;
int counter = 0;

int foo(int a, int b, int c) {
  if (a + b + c + d) {
    d = d - 1;
    return d + a + b + c + foo(a, b, c);
  } else {
    return d + a + b + c + 3;
  }
}

int main() { return foo(1, 2, 3); }
