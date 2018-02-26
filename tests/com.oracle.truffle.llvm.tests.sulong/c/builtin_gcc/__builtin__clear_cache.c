int main() {
  char a[100];
  __builtin___clear_cache(a, a + 100);
  return 0;
}
