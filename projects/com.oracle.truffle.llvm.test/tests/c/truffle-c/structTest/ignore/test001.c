struct str {
  char a;
  int b[];
} a3 = { 'o', { 1, 2 } };

int main() {
  struct str a2 = a3;
  return a2.b[0] + a2.b[1];
}
