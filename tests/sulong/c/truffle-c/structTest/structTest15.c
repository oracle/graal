struct {
  int a;
  int b;
} t;

int main() {
  t.a = 3;
  t.b = 2;
  t.a = 1;
  return t.a + t.b;
}
