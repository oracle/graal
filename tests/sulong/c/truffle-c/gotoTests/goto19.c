int main() {

  int i = 0;
  int res = 0;

  goto L1;

  for (i = 0; i < 10; i++) {

    goto L1;
    return 42;
  L1:
    res++;
  }

  return res;
}
