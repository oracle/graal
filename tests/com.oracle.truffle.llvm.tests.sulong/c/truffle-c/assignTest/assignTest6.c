int main() {
  int val = 5;
  int *pval = &val;
  int *b;
  int *c;
  int *e;
  e = b = c = pval;
  return *e;
}
