int main() {
  int a[3] = { 1, 2, 3 };
  int *p;
  int *pp;
  p = &a[2];
  pp = &((((((((((((((a[2]))))))))))))));
  return *p == *pp;
}
