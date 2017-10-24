#include <stdio.h>
#include <stdlib.h>

int main() {
  FILE *readableFile = fopen("this file should not exist", "r");
  if (readableFile == NULL) {
    printf("error opening file!\n");
    exit(0);
  } else {
    exit(-1);
  }
}
