#include <polyglot.h>

#define CALLBACK(str) { \
  void (*callback)(const char*) = polyglot_import("callback"); \
  callback(polyglot_from_string(str, "ascii")); \
}

__attribute__((constructor)) void ctor() { CALLBACK("construct\n"); }

__attribute__((destructor)) void dtor() { CALLBACK("destruct\n"); }
