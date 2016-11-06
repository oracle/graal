struct t {
  char a : 1;
  char b : 1;
  char c : 1;
  char d : 1;
  char e : 1;
  char f : 1;
  char g : 1;
  char h : 1;
};

union test {
  struct t adsf;
  char a;
};

int main() {
  union test t;
  t.a = 1;
  t.a = 4;
  t.a = 32;
  int sum = 10;
  sum += t.adsf.a;
  sum += t.adsf.b << 1;
  sum += t.adsf.c << 2;
  sum += t.adsf.d << 2;
  sum += t.adsf.e << 2;
  sum += t.adsf.f << 2;
  sum += t.adsf.g << 2;
  sum += t.adsf.h << 2;
  return sum;
}
