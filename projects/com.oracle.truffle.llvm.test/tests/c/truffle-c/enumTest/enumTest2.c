enum BOOLEAN {
  t = 0,
  f = 1
};

int main() {
  enum BOOLEAN a = f;
  enum BOOLEAN b = t;
  return a == b + a - b + b;
}
