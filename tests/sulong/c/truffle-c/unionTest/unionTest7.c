struct s1 {
  int a;
};

union u1 {
  struct s1 struct1;
};

int main() {
  union u1 u;
  u.struct1.a = 3;
  return u.struct1.a;
}
