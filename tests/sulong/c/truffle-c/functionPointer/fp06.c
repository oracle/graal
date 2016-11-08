
int func(int);

int main() {
  int (*fp)(int);

  fp = func;

  int (**fpp)(int) = &fp;
  return (*((fpp + 1) - 1))(2);
}

int func(int arg) { return arg + 1; }
