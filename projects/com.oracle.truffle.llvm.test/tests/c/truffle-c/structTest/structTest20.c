
struct t {
  int a;
  struct t2 {
    int b;
    double c;
  } s;
  int b;
  double c;
};

int main() {
  struct t str;
  str.s.c = 1.6;
  str.c = 1.6;
  return (int)(str.s.c + str.c);
}
