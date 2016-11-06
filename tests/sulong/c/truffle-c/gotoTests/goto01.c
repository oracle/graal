int main() {
  int a = 0;
L1:
  if (a == 1) {
    goto L2;
  }
  a++;
  goto L1;

L2:
  return a;
}
