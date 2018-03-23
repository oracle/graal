#include <polyglot.h>
#include <stdlib.h>

#define CALLBACK(str) { \
  void (*callback)(const char*) = polyglot_import("callback"); \
  callback(polyglot_from_string(str, "ascii")); \
}

void func() { CALLBACK("atexit\n"); }

__attribute__((constructor)) void ctor() {
  CALLBACK("construct\n");
  atexit(func);
}

__attribute__((destructor)) void dtor() { CALLBACK("destruct\n"); }
