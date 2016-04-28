union test {
  long a;
  int b;
  char c[2];
};

int main() {
  union test a = { .a = 4, .c[0] = 1, .c[1] = 2 };
  return a.b;
}
