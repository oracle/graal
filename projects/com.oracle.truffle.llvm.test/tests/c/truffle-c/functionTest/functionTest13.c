int a;
int b;

int func() {
  if (--a == 0) {
    return 3;
  }
  b++;
  return func();
}

int main() {
  a = 10;
  b = 0;
  int zero = func();
  return b + zero;
}
