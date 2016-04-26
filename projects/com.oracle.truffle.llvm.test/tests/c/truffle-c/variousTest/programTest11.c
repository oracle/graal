int func(int a, int *b) {
  int result = 0;
  int i;
  for (i = 0; i < a; i++) {
    result += (*b)--;
  }
  return result;
}

int main() {
  int asdf = 3;
  return asdf + func(4, &asdf) + asdf;
}
