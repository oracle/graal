int main() {

  int i;
  int res = 12;

  goto L1;

  i = 0;
  do {

    if (i > 100) {
    L1:

      res = 0;

      goto L2;
      break;
    }

  } while (i < 100);

L2:
  return res;
}
