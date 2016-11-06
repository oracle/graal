struct {
  char a;
  char b[][4];
} a3 = { 'o', { "wx", "ab" } };

int main() { return (a3.b[0][0] + a3.b[0][1] + a3.b[0][2] + a3.b[1][0] + a3.b[1][1] + a3.b[1][2]) % 256; }
