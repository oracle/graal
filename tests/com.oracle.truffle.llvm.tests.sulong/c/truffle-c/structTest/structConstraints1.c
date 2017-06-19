struct test {
  int a;
  double b;
  char c;
};

int main() { return sizeof(struct test) >= sizeof(int) + sizeof(double) + sizeof(char); }
