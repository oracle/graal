union test {
  int a;
};

int main() {
  union test asdf;
  asdf.a = 3;
  return asdf.a;
}
