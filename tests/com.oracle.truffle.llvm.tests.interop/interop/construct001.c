#include <truffle.h>

#define CALLBACK(str) { \
  void (*callback)(const char*) = truffle_import("callback"); \
  callback(truffle_read_string(str)); \
}

__attribute__((constructor)) void ctor() { CALLBACK("construct\n"); }

__attribute__((destructor)) void dtor() { CALLBACK("destruct\n"); }
