int main() {
  int a[1000];
  int i;
  for (i = 0; i < 1000; i++) {
    a[i] = i;
  }

  int s = 0;

  for (i = 0; i < 1000; i++) {
    s += a[i];
  }

  int s2 = 0;
  int *pa = a;

  for (i = 0; i < 1000; i++) {
    s2 += *(pa++);
  }

  return s == s2;
}
