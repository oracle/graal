#include <stdio.h>
#include <ctype.h>

typedef int (*test_type)(int c);

#define SIZE 11

test_type getFunction(int i) {
  int val = i % SIZE;
  switch (val) {
  case 0:
    return &isalnum;
  case 1:
    return &isalpha;
  case 2:
    return &iscntrl;
  case 3:
    return &isdigit;
  case 4:
    return &isgraph;
  case 5:
    return &islower;
  case 6:
    return &isprint;
  case 7:
    return &ispunct;
  case 8:
    return &isspace;
  case 9:
    return &isupper;
  case 10:
    return &isxdigit;
  }
  return 0;
}

int main() {
  for (int i = 0; i < 100; i++) {
    printf("%i\n", getFunction(i)(i % 2 == 0 ? 'a' : ' ') != 0);
  }
}
