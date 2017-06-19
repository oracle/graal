struct myStruct {
  int *p;
  int a;
};

int foo(struct myStruct *s) { return *(s->p) + s->a; }

int main() {
  struct myStruct s;
  int i = 5;
  s.p = &i;
  s.a = 54;
  return foo(&s);
}
