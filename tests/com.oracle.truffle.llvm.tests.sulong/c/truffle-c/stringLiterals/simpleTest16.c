int main() {
  char s[] = "asdf";
  char s2[] = "asdf";
  s[0] = 'X';
  return s[0] == s2[0];
}
