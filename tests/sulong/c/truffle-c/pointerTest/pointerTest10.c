int a = 5;

int *func() { return &a; }

int main() {
  int *nr = func();
  nr[0]++;
  return *func();
}
