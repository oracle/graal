#ifdef REF_COMPILER
#include <stdlib.h>
#endif

/*
int fannkuchredux(int n)
{
        int *a = (int *) malloc(n * 8);
        int i;
        for (i = 0; i < n; i++) {
                a[i] = 7842;
        }
        int sum = 0;
        for (i = 0; i < n; i++) {
                sum = (sum + a[i]) % 7842;
        }
        free(a);
        return sum;
} */

int static_var = 1;

int foo(int n) {
  int sum = 0;
  int i, j;
  for (j = 0; j < n; j++) {
    for (i = 0; i < n; i++) {
      sum = (sum + i) + 7842;
    }
  }
  static_var++;
  return sum;
}

int start(int a) {
  int n = 10;
  return foo(n + a);
}

int main() {
  int res = 0;
  res += start(1);
  res += start(2);
  return (res + start(3)) % 128;
}
