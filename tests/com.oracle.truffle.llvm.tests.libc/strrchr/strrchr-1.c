#include <string.h>
#include <stdio.h>

int main() {
  const char *str = "The quick brown fox jumps over the lazy dog";
  printf("%s\n", strrchr(str, '\0'));
  printf("%s\n", strrchr(str, 't'));
  printf("%s\n", strrchr(str, 'e'));
  printf("%s\n", strrchr(str, 'g'));
  printf("%s\n", strrchr(str, 'r'));
  printf("%s\n", strrchr(str, 'g'));
  printf("%s\n", strrchr(str, 'Z'));
}
