int main() {

  int i;
  int res = 0;

  goto L1;

  for (i = 0; i < 10; i++) {

  L2:

    res++;
    goto L3;
    return 42;
  L3:
    if (i > 100) {
    L1:
      i = 0;
      goto L2;
      break;
    }
  }

  return res;
}
