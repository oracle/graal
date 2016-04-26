struct {
  char a;
  char b[][4];
} a3 = { 'o', { "wx" } };

int main() { return a3.b[0][0] + a3.b[0][1] + a3.b[0][2]; }
