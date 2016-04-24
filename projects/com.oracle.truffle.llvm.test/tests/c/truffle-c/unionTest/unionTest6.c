union test1 {
  int a;
};

union test2 {
  union test1 t1;
};

int main() {
  union test2 t1;
  union test2 t2;
  t1.t1.a = 3;
  t2.t1.a = 7;
  return t1.t1.a + t2.t1.a;
}
