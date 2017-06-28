int main() {
  int *p;
  int a;
  p = &a;
  *p = 3;
  p++;
  p--;
  return *p == a;
}
