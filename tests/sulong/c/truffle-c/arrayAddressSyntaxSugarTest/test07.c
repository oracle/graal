int foo(char a[], char b[]) {
  int s = 0;
  if (a == &a)
    s += 1;
  if (a == &a[0])
    s += 2;
  if (b == a)
    s += 4;
  return s;
}

int main() {
  char str[] = "abc";
  int s = foo(str, "abc");
  if (str == &str)
    s += 8;
  if (str == &str[0])
    s += 16;

  return s;
}
