#include <stdlib.h>

int main() {
  if (__alignof__(char)!= 1) {
    abort();
  }
  if (__alignof__(unsigned char)!= 1) {
    abort();
  }
  if (__alignof__(signed char)!= 1) {
    abort();
  }
  if (__alignof__(short)!= 2) {
    abort();
  }
  if (__alignof__(short int)!= 2) {
    abort();
  }
  if (__alignof__(signed short)!= 2) {
    abort();
  }
  if (__alignof__(signed short int)!= 2) {
    abort();
  }
  if (__alignof__(unsigned short)!= 2) {
    abort();
  }
  if (__alignof__(unsigned short int)!= 2) {
    abort();
  }
  if (__alignof__(int)!= 4) {
    abort();
  }
  if (__alignof__(signed int)!= 4) {
    abort();
  }
  if (__alignof__(unsigned)!= 4) {
    abort();
  }
  if (__alignof__(unsigned int)!= 4) {
    abort();
  }
  if (__alignof__(long)!= 8) {
    abort();
  }
  if (__alignof__(float)!= 8) {
    abort();
  }
  if (__alignof__(double)!= 8) {
    abort();
  }
  if (__alignof__(int *)!= 8) {
    abort();
  }
  return 0;
}
