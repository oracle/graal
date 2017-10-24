#include <stdio.h>
#include <string.h>

#define MAX_ERROR_INDEX 100 // conservative value

int main() {
  printf("const char* error_numbers[%d] = {", MAX_ERROR_INDEX + 1);
  for (int i = 0; i <= MAX_ERROR_INDEX; i++) {
    if (i != 0) {
      putchar(',');
    }
    printf("\n\t\"%s\"", strerror(i));
  }
  puts("\n};");
}
