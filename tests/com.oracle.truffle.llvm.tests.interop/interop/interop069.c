#include <polyglot.h>

void *registered_tagged_value;

void *registered_tagged_address() {
  return registered_tagged_value;
}

int main() {
  registered_tagged_value = (void *) polyglot_import("a");
  return 0;
}
