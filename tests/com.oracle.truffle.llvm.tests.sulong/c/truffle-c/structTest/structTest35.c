struct test {
  int a;
};

int t() {
  static struct test a = { 3 };
  return a.a++;
}

int main() { return t() + t() + t(); }
