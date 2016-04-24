int func(int t) { return t; }

int other(int t) { return 2; }

int main() {
  int (*test[4])() = { func, other, func, func };
  return test[0](4) + test[1](5) + test[3](7) * 5;
}
