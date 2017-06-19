int a[1] = { 42 };
int main() {
  return 0;
  // segfaults on gcc as well:
  // return (( (int**) &a)[0])[0];
}
