int *func() {
  static int a = 5;
  return &a;
}

int main() { return *func(); }
