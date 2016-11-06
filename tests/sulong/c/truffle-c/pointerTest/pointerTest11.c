int a = 5;

int *func() { return &a; }

int main() {
  int *nr = func();
  (*(nr + 1 - 1))++;
  return *func();
}
