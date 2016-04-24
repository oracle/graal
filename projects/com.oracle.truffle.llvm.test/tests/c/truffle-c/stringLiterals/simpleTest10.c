long function(char t[4]) { return t; }

int main() {
  long t1 = function("asdf");
  long t2 = function("asdf");
  return t1 == t2 && t1 == "asdf";
}
