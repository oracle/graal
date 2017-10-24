#include <stdio.h>
#include <string.h>

int main() {
  printf("%ld\n", strspn("129asdf", "1234567890"));
  printf("%ld\n", strspn("1234567890", "129asdf"));
  printf("%ld\n", strspn("asdf", "asdf"));
  printf("%ld\n", strspn("1", "1"));
  printf("%ld\n", strspn("1", ""));
  printf("%ld\n", strspn("", "1"));
  printf("%ld\n", strspn("hello world!", "test hello!"));
}
