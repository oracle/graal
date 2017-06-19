long function(char t[4]) { return (long)t; }

int main() {
  long adr = function("asdf");
  return adr == "asdf";
}
