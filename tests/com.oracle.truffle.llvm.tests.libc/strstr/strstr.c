#include <stdio.h>
#include <string.h>
#include <stdlib.h>

int main() {
  puts(strstr("", ""));
  if (strstr("asdf", "asdfg") != NULL) {
    abort();
  }
  if (strstr("", "asdf") != NULL) {
    abort();
  }
  const char *haystack = "The quick brown fox jumps over the lazy dog";
  puts(strstr(haystack, ""));
  puts(strstr(haystack, "the"));
  puts(strstr(haystack, "he"));
  puts(strstr(haystack, "quick "));
  puts(strstr(haystack, "over the"));
  puts(strstr(haystack, " lazy"));
  if (strstr(haystack, " lazz") != NULL) {
    abort();
  }
}
