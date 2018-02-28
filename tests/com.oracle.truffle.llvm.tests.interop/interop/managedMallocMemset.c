#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <truffle.h>

typedef void* VALUE;

int main() {
  VALUE *array = truffle_managed_malloc(sizeof(VALUE) * 4);
  for (int i = 0; i < 4; i++) {
    array[i] = (VALUE) (i+1);
  }
  memset(array+1, 0, 2*sizeof(VALUE));

  if (array[0] == 1 && array[1] == NULL && array[2] == NULL && array[3] == 4) {
    return 0;
  } else {
    for (int i = 0; i < 4; i++) {
      printf("%d\n", (int) array[i]);
    }
    return 1;
  }
}
