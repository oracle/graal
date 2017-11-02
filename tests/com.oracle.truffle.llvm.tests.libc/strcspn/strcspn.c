#include <stdio.h>
#include <string.h>

int main() {
  printf("%ld\n", strcspn("129asdf", "1234567890"));
  printf("%ld\n", strcspn("1234567890", "129asdf"));
  printf("%ld\n", strcspn("asdf", "asdf"));
  printf("%ld\n", strcspn("1", "1"));
  printf("%ld\n", strcspn("1", ""));
  printf("%ld\n", strcspn("", "1"));
  printf("%ld\n", strcspn("hello world!", "test hello!"));
  printf("%ld\n", strcspn("asdfajklerawrhjyxcv123", "992!"));
}
