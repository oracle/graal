int a[15];

int main() {
  int i;
  for (i = 0; i < 15; i++) {
    a[i] = 1;
  }
  for (i = 0; i < 15; i++) {
    *a += a[i];
  }
  return *a;
}
