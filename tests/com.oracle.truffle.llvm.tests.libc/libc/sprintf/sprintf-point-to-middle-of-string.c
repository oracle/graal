#include <string.h>
#include <stdio.h>
#include <math.h>

int main() {
  char str[80];
  memset(str, 'a', 20);
  int length = sprintf(&str[10], "asdfasdf %d %s\n", 43, "asdfasdfvyx");
  puts(str);
  return length;
}
