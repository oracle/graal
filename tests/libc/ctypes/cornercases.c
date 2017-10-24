#include <stdio.h>
#include <ctype.h>

int main() {
  printf("%d\n", isalpha(-128));
  printf("%d\n", isalpha(255));
}
