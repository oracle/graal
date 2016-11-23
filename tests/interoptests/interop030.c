#include <truffle.h>

typedef struct {
  bool valueBool;
  char valueB;
  short valueC;
  int valueI;
  long valueL;
  float valueF;
  double valueD;
} CLASS;

int getValueI(CLASS *c) { return c->valueI; }

int main() { return 0; }
