#include "cpuid.h"

int main() {
  return has_rdrand() ? 1 : 0;
}
