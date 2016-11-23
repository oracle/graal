#include <string.h>

int main() { return 0; }

int func(const char *str1, const char *str2) { return strcmp(str1, str2); }

const char *native_str = "foo";

int compare_with_native(const char *str) { return strcmp(native_str, str); }
