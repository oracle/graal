#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");

  double sum = 0;
  sum += ((int (*)(int)) polyglot_get_member(obj, "addI"))(1);         // 4
  sum += ((char (*)(char)) polyglot_get_member(obj, "addB"))(2);       // 3
  sum += ((long (*)(long)) polyglot_get_member(obj, "addL"))(3);       // 7
  sum += ((float (*)(float)) polyglot_get_member(obj, "addF"))(4.5);   // 10
  sum += ((double (*)(double)) polyglot_get_member(obj, "addD"))(5.5); // 12
  return sum;                                                          // 36
}
