#include <stdio.h>
#include <stdlib.h>

int main() {
  FILE *file = fopen(__FILE__, "r");
  char arr[1024];
  while (fgets(arr, 1024, file) == arr) {
    printf("%s", arr);
  }
}
