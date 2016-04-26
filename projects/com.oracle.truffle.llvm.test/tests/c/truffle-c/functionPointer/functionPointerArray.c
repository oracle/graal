int func() { return 1; }

int other() { return 2; }

int main() {
  int (*test[4])() = { func, other, func, func };
  return test[0]() + test[1]() + test[3]() * 5;
}
