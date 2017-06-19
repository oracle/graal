
char *g = "abc";
char k[] = "abc";

char *foo() { return "abc"; }

int main() {
  char *p = "abc";
  char c[] = "abc";
  if (p != g)
    exit(1);
  if (p != foo())
    exit(2);
  if (p == c)
    exit(3);
  if (p == k)
    exit(4);
  if (c == k)
    exit(5);
  return 0;
}
