#include <stdio.h>
#include <stdlib.h>

int main() {
  if (remove("this test file should not exist")) {
    printf("asdf");
  }
}
