
int main() {
  return 0;
  // segfaults on gcc as well
  // int a[1] = {42};
  // int **p = &a;
  // return ((p)[0])[0];
}
