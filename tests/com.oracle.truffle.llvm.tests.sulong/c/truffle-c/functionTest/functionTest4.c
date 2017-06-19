int foo(int *a);

int main() {
  int val = 5;
  foo(&val);
  return val;
}

int foo(int *a) { *a = *a * 2 + 1; }
