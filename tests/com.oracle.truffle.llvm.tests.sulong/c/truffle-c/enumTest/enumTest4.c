enum E1 {
  B1 = 3
};

enum E2 {
  B2 = 3
};

int main() {
  enum E1 e1 = B1;
  enum E2 e2 = B2;
  return e1 == (int)e2;
}
