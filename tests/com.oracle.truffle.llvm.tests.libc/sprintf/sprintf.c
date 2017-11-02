#include <stdio.h>
#include <math.h>

int main() {
  char str[80];

  int length = sprintf(str, "asdfasdf %d %s\n", 43, "asdfasdfvyx");
  puts(str);
  return length;
}
