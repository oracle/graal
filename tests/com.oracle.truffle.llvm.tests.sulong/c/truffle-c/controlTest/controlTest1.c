int foo(int i) {
  if (i == 0) {
    return 0;
  }
  if (i == 1) {
    return 1;
  }
  if (i == 2) {
    return 2;
  }
  if (i == 3) {
    return 3;
  }
  if (i == 4) {
    return 4;
  }
  return -1;
}

int main() { return foo(0) + foo(1) + foo(2) + foo(3) + foo(4); }
