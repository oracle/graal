int *func(int a, int *b) {
  static int result = 0;
  int i;
  for (i = 0; i < a; i++) {
    result += (*b)--;
  }
  return &result;
}

int main() {
  int asdf = 3;
  return *func(5, func(asdf, func(4, &asdf))) % 128;
}
