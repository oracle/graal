int cont_add(int a) {
  static int b = 3;
  return b += a;
}

int main() {
  cont_add(4);
  return cont_add(7);
}
