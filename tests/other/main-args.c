#include <stdio.h>

int main(int argc, char **argv) {
  int checksum = argc;
  for (int i = 1; i < argc; i++) {
    int j = 0;
    while (argv[i][j] != '\0') {
      checksum += argv[i][j];
      j++;
    }
  }
  return checksum % 256;
}
