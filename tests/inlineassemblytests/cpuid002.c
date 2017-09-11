#include <cpuid.h>
#include <string.h>

typedef union {
  struct {
    unsigned int b, d, c;
  };
  char str[13];
} VENDOR;

int main(void)
{
  VENDOR vendor;
  unsigned int a, b, c, d;

  __cpuid(0, a, vendor.b, vendor.c, vendor.d);
  vendor.str[12] = 0;
  if(!strcmp(vendor.str, "GenuineIntel")) {
    return 1;
  }
  if(!strcmp(vendor.str, "AuthenticAMD")) {
    return 1;
  }
  if(!strcmp(vendor.str, "KVMKVMKVM")) {
    return 1;
  }
  if(!strcmp(vendor.str, "SulongLLVM64")) {
    return 1;
  }

  return 0;
}
