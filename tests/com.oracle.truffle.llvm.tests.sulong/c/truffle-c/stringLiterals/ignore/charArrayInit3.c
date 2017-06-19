struct {
  char a;
  char b[2];
} a3 = { 'o', { "wx" } };

int main() { return (a3.b[0] + a3.b[1] + a3.b[2]) % 128; }
