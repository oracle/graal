int main() {

  int i;
  int res = 12;

  goto L1;

  for (i = 0; i < 100; i++) {

    if (i > 100) {
    L1:

      res = 0;

      goto L2;
    }
  }

L2:
  return res;
}
