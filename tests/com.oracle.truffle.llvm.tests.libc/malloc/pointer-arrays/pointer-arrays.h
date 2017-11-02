#include <stdlib.h>

int main() {
  TYPE a = 3;
  TYPE b = 2;
  TYPE **ptr_arr = malloc(sizeof(TYPE *) * 3);
  ptr_arr[0] = &a;
  ptr_arr[1] = &b;
  ptr_arr[2] = &b;
  b = 5;
  return *(ptr_arr[0]) + *(ptr_arr[1]) + *(ptr_arr[2]);
}
