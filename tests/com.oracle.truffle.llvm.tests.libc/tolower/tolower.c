#include <stdlib.h>
#include <ctype.h>

int main() {
  if (tolower('a') != 'a') {
    abort();
  }
  if (tolower('z') != 'z') {
    abort();
  }
  if (tolower('!') != '!') {
    abort();
  }
  if (tolower('A') != 'a') {
    abort();
  }
  if (tolower('Z') != 'z') {
    abort();
  }
  if (tolower('@') != '@') {
    abort();
  }
  if (tolower('[') != '[') {
    abort();
  }
  if (tolower('1') != '1') {
    abort();
  }
}
