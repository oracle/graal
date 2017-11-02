#include <stdio.h>
#include <ctype.h>

int main() {
  printf("%d\n", isalpha(-128));
#ifdef __linux__ 
  printf("%d\n", isalpha(255));
#endif
}
