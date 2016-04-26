int main() {
  int *p;
  int a;
  p = &a;
  *(p++) = 3;
  --p;
  return *p;
}
