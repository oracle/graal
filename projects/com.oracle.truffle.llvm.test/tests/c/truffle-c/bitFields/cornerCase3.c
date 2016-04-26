struct {
  unsigned char a : 1;
  unsigned char b : 3;
  unsigned char c : 3;
  struct {
    unsigned long a : 1;
    unsigned long b : 3;
    unsigned long c : 3;
  } y;
} x = { 3, 1, 2 };

int main() {
  x.y.b = 125;
  return x.a + x.b + x.c + x.y.a + x.y.b + x.y.c;
}
