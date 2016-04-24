int main() {
  goto L1;
  int a, b;
L2:
  b = 2;
  goto L3;
L1:
  goto L2;
L3:
  return b;
}
