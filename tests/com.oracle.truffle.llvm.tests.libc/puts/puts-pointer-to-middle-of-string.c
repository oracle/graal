#include <string.h>
#include <stdio.h>

char str[50];

int main() {
  memset(str, 'a', sizeof(str) - 2);
  str[sizeof(str) - 1] = 0;
  puts(&str[10]);
}
