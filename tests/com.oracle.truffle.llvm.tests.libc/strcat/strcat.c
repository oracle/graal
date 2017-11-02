#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  char arr[100];
  strcpy(arr, "hello world!");
  int start = strlen(arr);
  char *result = strcat(arr, " appended string");
  if (result != arr) {
    abort();
  }
  printf("%s\n", result);

  result = strcat(arr, " again appended!");
  if (result != arr) {
    abort();
  }
  printf("%s\n", result);
  result = strcat(arr, "");
  if (result != arr) {
    abort();
  }
  printf("%s\n", result);
  result = strcat(arr, "a");
  if (result != arr) {
    abort();
  }
  printf("%s\n", result);
}
