#include <polyglot.h>
#include <truffle.h>

int main() {
  void *object = polyglot_import("object");
  void *handle1 = truffle_handle_for_managed(object);
  void *handle2 = truffle_handle_for_managed(object);

  if (!truffle_is_handle_to_managed(handle1)) {
    return 1;
  }
  if (!truffle_is_handle_to_managed(handle2)) {
    return 2;
  }

  truffle_release_handle(handle2);

  if (!truffle_is_handle_to_managed(handle1)) {
    return 3;
  }

  truffle_release_handle(handle1);

  if (truffle_is_handle_to_managed(handle1)) {
    return 4;
  }

  return 0;
}
