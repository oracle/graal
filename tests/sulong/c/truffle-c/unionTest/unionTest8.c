int a1 = 1;

struct test1 {
  int a;
};

int a2 = 1;

struct test2 {
  int a;
  int b;
};

union test3 {
  struct test1 t1;
  struct test2 t2;
};

int main() {
  static int a3 = 1;
  union test3 t3;
  t3.t2.a = 4;
  t3.t2.b = 5;
  t3.t1.a = 6;
  static int a4 = 1;
  return t3.t2.a + t3.t2.b + t3.t1.a - a1 - a2 - a3 - a4;
}
