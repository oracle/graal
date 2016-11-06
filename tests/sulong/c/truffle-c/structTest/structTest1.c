struct myStruct {
  int a;
  int b;
};

void foo(struct myStruct *s) { s->b = 123; }

int main() {
  struct myStruct s;
  s.a = 77;
  s.b = 66;
  foo(&s);
  return s.b;
}
