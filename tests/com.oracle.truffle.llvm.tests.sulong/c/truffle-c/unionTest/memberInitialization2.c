union test {
  long a;
  int b;
  char c[2];
} a = { -1 };

int main() { return 10 + a.c[0]; }
