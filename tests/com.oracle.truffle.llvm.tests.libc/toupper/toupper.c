#include <stdlib.h>
#include <ctype.h>

int main() {
  if (toupper('a') != 'A') {
    abort();
  }
  if (toupper('z') != 'Z') {
    abort();
  }
  if (toupper('!') != '!') {
    abort();
  }
  if (toupper('A') != 'A') {
    abort();
  }
  if (toupper('Z') != 'Z') {
    abort();
  }
  if (toupper('@') != '@') {
    abort();
  }
  if (toupper('[') != '[') {
    abort();
  }
  if (toupper('1') != '1') {
    abort();
  }
}
