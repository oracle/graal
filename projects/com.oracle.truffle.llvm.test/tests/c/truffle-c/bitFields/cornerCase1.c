struct {
  unsigned char a : 1;
  unsigned char b : 3;
  unsigned char c : 3;
} x = { 3, 1, 2 };

int main() { return x.a + x.b + x.c; }
