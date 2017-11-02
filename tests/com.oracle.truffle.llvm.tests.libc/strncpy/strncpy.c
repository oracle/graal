#include <string.h>
#include <stdio.h>

int main() {
  char str1[] = "hello world";
  char str2[40];
  char str3[40];

  strncpy(str2, str1, sizeof(str2));

  strncpy(str3, str2, 3);
  str3[3] = '\0';

  puts(str1);
  puts(str2);
  puts(str3);
}
