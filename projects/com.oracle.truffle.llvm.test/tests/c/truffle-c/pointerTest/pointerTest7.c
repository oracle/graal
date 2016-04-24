void swap(int *a, int *b) {
  int t = *a;
  *a = *b;
  *b = t;
}

int main() {
  int a = 5;
  int b = 1;
  int *pa = &a;
  int *pb = &b;
  swap(pa, pb);
  return *pa * 2 + *pb;
}
