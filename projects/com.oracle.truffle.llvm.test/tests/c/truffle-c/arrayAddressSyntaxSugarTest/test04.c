long function(char test[]) { return (long)test; }

int main() {
  long a = function("asdf");
  long b = function("asdf");
  return a == b;
}
