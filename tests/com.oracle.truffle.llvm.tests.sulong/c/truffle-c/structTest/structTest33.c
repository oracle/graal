struct test {
  double asdf[3];
  int jkl[2];
  char nm[1];
};

int main() {
  struct test t = { { 1.5, 2.5, 3.5 }, { 1, 2 }, { 'a' } };
  return t.asdf[0] + t.asdf[1] + t.asdf[2] + t.jkl[0] + t.jkl[1] + t.nm[0];
}
