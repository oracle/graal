int main() {
  char t[] = "asdf";
  char s[] = "asdf";
  s[0] = 'A';
  return s[0] == t[0];
}
