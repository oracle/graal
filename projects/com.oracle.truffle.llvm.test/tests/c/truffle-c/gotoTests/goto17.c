int main() {

  int i;
  int res = 0;

  goto L1;

  do {

  L2:

    res++;
    goto L3;

  L1:
    i = 0;
    goto L2;

  L3:
    i++;

  } while (i < 10);

  return res;
}
