#include <truffle.h>
#include <stdlib.h>

#define CALLBACK(str) { \
  void (*callback)(const char*) = truffle_import("callback"); \
  callback(truffle_read_string(str)); \
}

void func() { CALLBACK("atexit\n"); }

__attribute__((constructor)) void ctor() {
  CALLBACK("construct\n");
  atexit(func);
}

__attribute__((destructor)) void dtor() { CALLBACK("destruct\n"); }
