union test {
  int a;
  int b[10];
};

int main() {
  union test a;
  int i;
  for (i = 0; i < 10; i++) {
    a.b[i] = i + 1;
  }
  return a.a + a.b[3];
}
