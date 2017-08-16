#include <cpuid.h>

#define	RDRND	(1 << 30)
#define	RDSEED	(1 << 18)

static inline int has_rdrand() {
  unsigned int a;
  unsigned int b;
  unsigned int c;
  unsigned int d;
  if(!__get_cpuid(0x1, &a, &b, &c, &d))
    return 0;
  return c & RDRND;
}

static inline int has_rdseed() {
  unsigned int a;
  unsigned int b;
  unsigned int c;
  unsigned int d;
  if(!__get_cpuid(0x7, &a, &b, &c, &d))
    return 0;
  return b & RDSEED;
}
