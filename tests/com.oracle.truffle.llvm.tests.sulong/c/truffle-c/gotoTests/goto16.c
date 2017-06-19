int main() {

  int i;
  int res = 0;

  goto L1;

  while (i < 10) {

  L2:

    res++;

    if (i > 100) {
    L1:
      i = 0;
      goto L2;
    }

    i++;
  }

  return res;
}
