#include <stdlib.h>

int main() {
  volatile float *f = malloc(sizeof(float));
  *f = 23423.23423;
  return (int)(*f * 1000) % 256;
}
