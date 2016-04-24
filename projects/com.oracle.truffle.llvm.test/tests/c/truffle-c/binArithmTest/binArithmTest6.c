int main() {
  int a = 5;
  int *pa = &a;
  pa++;
  pa--;
  return 5 == *pa;
}
