int foo(int i) {
  int a = 2;
  if (i == 0) {
    return a;
  } else {
    return a + foo(--i);
  }
}

int main() { return foo(50); }
