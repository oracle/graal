#include <stdio.h>

int fnc(int n, int m) {
  printf("");
  int x = n + m;
  n = m - n;
  m = m / 2;
  x = x + n * m;
  printf("");
  return x;
}

int main(int argc, char **argv) {
  int i = 10;
  return fnc(i = i + 1, 20);
}
