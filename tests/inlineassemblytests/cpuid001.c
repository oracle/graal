#include <immintrin.h>
#include "cpuid.h"

int main() {
#ifdef __RDRND__
  return has_rdrand() ? 1 : 0;
#else
  return 1;
#endif
}
