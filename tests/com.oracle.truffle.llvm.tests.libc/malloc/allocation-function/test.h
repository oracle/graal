#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>

TYPE *allocate() { return malloc(sizeof(TYPE) * 5); }

int main() {
  TYPE *arr = allocate();
  if (arr == NULL) {
    abort();
  } else if (arr != NULL) {
    arr[0] = (TYPE)5;
    arr[1] = (TYPE)9;
    return arr[0] + arr[1];
  } else {
    abort();
  }
}
