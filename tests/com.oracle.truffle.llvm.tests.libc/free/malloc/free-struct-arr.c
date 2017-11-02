#include <stdlib.h>

struct test {
  int a;
  int b;
};
int main() {
  struct test t1 = { 1, 4 };
  struct test t2 = { 5, 7 };
  struct test t3 = { 9, 3 };
  struct test *arr = malloc(3 * sizeof(struct test));
  arr[0] = t1;
  arr[1] = t2;
  arr[2] = t3;
  int sum = 0;
  for (int i = 0; i < 3; i++) {
    sum += arr[i].a * 2 - arr[i].b;
  }
  free(arr);
  return sum;
}
