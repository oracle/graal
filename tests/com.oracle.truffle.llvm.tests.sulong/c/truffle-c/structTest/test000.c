struct tiny {
  int c;
};

main() {
  struct tiny x[3];
  x[0].c = 10;
  x[1].c = 11;
  x[2].c = 12;
  struct tiny a = x[2];

  return a.c;
}
