struct test {
  int a;
  char b[1];
  int c;
} asdf = { 55, 'b', 123 };

int main() { return (asdf.a + asdf.b[0] + asdf.c) % 256; }
