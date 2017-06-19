long function(char test[]) { return (long)test; }

int main() {
  char arr[] = "asdf";
  long a = function(arr);
  long b = function(arr);
  return a == b;
}
