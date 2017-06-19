long function(char test[]) { return (long)&test[0]; }

int main() {
  long a = function("asdf");
  long b = function("asdf");
  return a == b;
}
