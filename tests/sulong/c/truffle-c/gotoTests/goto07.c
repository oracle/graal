int main() {

  int i;
  int res = 12;

  goto L1;

  for (i = 0; i < 100; i++) {

  L3:

    res = res + 1;

    goto L2;

    if (i > 100) {
    L1:

      res = 0;

      goto L3;
    }
  }

L2:
  return res;
}
