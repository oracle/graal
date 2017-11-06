#include <string.h>
#include <stdio.h>

int main() {
  char str[] = "Hello world - this is a string-hello!";
  const char s[] = "";
  char *token;

  token = strtok(str, s);
  while (token != NULL) {
    printf("%s\n", token);
    token = strtok(NULL, s);
  }
}
