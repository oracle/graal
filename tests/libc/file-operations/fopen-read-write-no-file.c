#include <stdio.h>
#include <stdlib.h>

int main() {
  remove("sulong_test_file");
  FILE *writeableFile = fopen("sulong_test_file", "r+");
  if (writeableFile != NULL) {
    exit(1);
  }
}
