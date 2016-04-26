
int main() {
  int a[1] = { 42 };
  int *p = a;
  return ((&p)[0])[0];
}
