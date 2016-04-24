struct myStruct {
  int a;
  int b;
};

int main() {
  struct myStruct s;
  s.a = 5;
  s.b = 10;
  return s.a + s.b;
}
