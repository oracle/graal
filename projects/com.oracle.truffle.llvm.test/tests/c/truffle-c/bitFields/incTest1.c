struct test {
  int a : 1;
};

int main() {
  struct test t = { 0 };
  t.a++;
  return t.a++ + 1;
}
