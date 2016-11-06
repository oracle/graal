long f1() { return "asdf"; }

long f2() {
  long t = "asdfasdf";
  return "asdf";
}

int main() { return f1() == f2(); }
