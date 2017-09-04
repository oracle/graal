#include <truffle.h>

typedef struct {
  double real;
  double imaginary;
} COMPLEX;

void complexAdd(COMPLEX *a, COMPLEX *b) {
  a->real = a->real + b->real;
  a->imaginary = a->imaginary + b->imaginary;
}

int main() { return 0; }
