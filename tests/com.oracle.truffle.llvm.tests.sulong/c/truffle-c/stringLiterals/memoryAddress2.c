long function(char test[]) { return (long)test; }

long func() {
  long a = function("asdf");
  return a;
}

int main() {
  long a = func();
  long b = func();
  return a == b;
}
