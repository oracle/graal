#include <string.h>
#include <stdio.h>

int main() {
  char arr[50];
  memset(arr, 'a', 50 * sizeof(char));
  strncpy(arr, "hello world!", 50);
  printf("%s\n", arr);
}
