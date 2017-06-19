struct myStruct {
  int *p;
  int b;
};

int main() {
  struct myStruct s;
  int a = 123;
  s.p = &a;
  return *(s.p);
}
