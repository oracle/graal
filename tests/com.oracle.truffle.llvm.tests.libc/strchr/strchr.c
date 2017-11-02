#include <string.h>
#include <stdlib.h>

int main() {
  char *helloWorld = "hello world";
  char *empty = "";
  if (strchr(helloWorld, 'a') != NULL) {
    abort();
  }
  if (strchr("a", 'b') != NULL) {
    abort();
  }
  if (strchr("a", 'A') != NULL) {
    abort();
  }
  if (strchr(empty, '\0') != empty) {
    abort();
  }
  if (strchr(helloWorld, 'h') != helloWorld) {
    abort();
  }
  if (strchr(helloWorld, 'e') != &helloWorld[1]) {
    abort();
  }
  if (strchr(helloWorld, 'l') != &helloWorld[2]) {
    abort();
  }
  if (strchr(helloWorld, 'o') != &helloWorld[4]) {
    abort();
  }
  if (strchr(helloWorld, ' ') != &helloWorld[5]) {
    abort();
  }
  if (strchr(helloWorld, 'w') != &helloWorld[6]) {
    abort();
  }
}
