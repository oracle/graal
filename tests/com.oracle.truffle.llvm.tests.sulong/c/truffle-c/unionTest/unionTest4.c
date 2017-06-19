union u {
  int a[3];
};

int main() {
  int asdf[2];
  return sizeof(union u) > sizeof(asdf);
}
