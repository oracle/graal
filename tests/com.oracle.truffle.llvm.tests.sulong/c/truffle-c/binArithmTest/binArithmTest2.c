int main() {
  int a;
  int *pa;
  int b;
  int *pb;
  pa = &a;
  pb = &b;
  a = 2;
  b = 3;
  int c;
  int *pc;
  pc = &c;
  *pc = *pa + *pb;
  return c;
}
