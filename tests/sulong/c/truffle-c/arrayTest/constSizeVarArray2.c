char n = 5;

int func() {
  int x[3 * n];
  return &x == x;
}

int main() { return func(); }
