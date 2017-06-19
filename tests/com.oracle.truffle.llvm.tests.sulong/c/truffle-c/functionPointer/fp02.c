
int func(int);

int main() {
  int (*fp)(int);

  fp = func;

  return fp(2);
}

int func(int arg) { return arg + 1; }
