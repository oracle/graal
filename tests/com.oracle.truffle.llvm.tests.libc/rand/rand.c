#include <stdio.h>
#include <stdlib.h>

int main() {
  int arr1[50];
  int arr2[50];
  for (int i = 0; i < 50; i++) {
    arr1[i] = rand();
    arr2[i] = rand();
  }
  for (int i = 0; i < 50; i++) {
    if (arr1[i] != arr2[i]) {
      return 0;
    }
  }
  return 1;
}
