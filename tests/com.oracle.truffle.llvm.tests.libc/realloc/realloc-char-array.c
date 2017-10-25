#include <stdlib.h>
#include <string.h>
#include <stdio.h>

int main() {
  char *mal = malloc(sizeof(char) * 10);
  memcpy(mal, "asdfasdfasdf", 5);
  char *addr = mal;
  for (int i = 0; i < 100; i++) {
    addr = (char *)realloc(addr, sizeof(char) * 15);
    for (int i = 5; i < 14; i++) {
      addr[i] = 'a' + i;
    }
    addr[14] = 0;
  }
  printf("%s\n", addr);
}
