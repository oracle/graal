#include <stdio.h>

long long int test1 = 2852126723232139342;
long test2 = 2852126723232139342;

int main() {
  printf("%lld\n", test1);
  printf("%llu\n", test1);
  printf("%ld\n", test2);
  printf("%lu\n", test2);
}
