

struct {
  char a;
  char b[];
} a3 = { 'o', "ab" };

int main() { return a3.b[0] + a3.b[1]; }
