struct test {
  int a;
};

int main() {
  static struct test t = {.a = 3 };
  return t.a;
}
