#include <stdlib.h>

int array4D[2][2][3][2] = { { { { 0, 1 }, { 2, 3 }, { 3, 4 } }, { { 5, 6 }, { 7, 8 }, { 9, 10 } }, },
                            { { { 11, 12 }, { 13, 14 }, { 14, 15 } }, { { 16, 17 }, { 18, 19 }, { 20, 21 } } } };

int main() {
  if (array4D[0][0][0][0] != 0) {
    abort();
  }
  if (array4D[0][0][0][1] != 1) {
    abort();
  }
  if (array4D[0][0][1][0] != 2) {
    abort();
  }
  if (array4D[0][0][1][1] != 3) {
    abort();
  }
  if (array4D[0][0][2][0] != 3) {
    abort();
  }
  if (array4D[0][0][2][1] != 4) {
    abort();
  }
  if (array4D[0][1][0][0] != 5) {
    abort();
  }
  if (array4D[0][1][0][1] != 6) {
    abort();
  }
  if (array4D[0][1][1][0] != 7) {
    abort();
  }
  if (array4D[0][1][1][1] != 8) {
    abort();
  }
  if (array4D[0][1][2][0] != 9) {
    abort();
  }
  if (array4D[0][1][2][1] != 10) {
    abort();
  }
  if (array4D[1][0][0][0] != 11) {
    abort();
  }
  if (array4D[1][0][0][1] != 12) {
    abort();
  }
  if (array4D[1][0][1][0] != 13) {
    abort();
  }
  if (array4D[1][0][1][1] != 14) {
    abort();
  }
  if (array4D[1][0][2][0] != 14) {
    abort();
  }
  if (array4D[1][0][2][1] != 15) {
    abort();
  }
  if (array4D[1][1][0][0] != 16) {
    abort();
  }
  if (array4D[1][1][0][1] != 17) {
    abort();
  }
  if (array4D[1][1][1][0] != 18) {
    abort();
  }
  if (array4D[1][1][1][1] != 19) {
    abort();
  }
  if (array4D[1][1][2][0] != 20) {
    abort();
  }
  if (array4D[1][1][2][1] != 21) {
    abort();
  }
}
