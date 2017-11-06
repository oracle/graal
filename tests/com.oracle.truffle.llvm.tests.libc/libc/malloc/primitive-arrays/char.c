#include <stdlib.h>

int main() {
  volatile char *arr = malloc(10 * sizeof(char));
  arr[5] = 'a';
  arr[4] = 'b';
  return arr[5] + arr[4];
}
