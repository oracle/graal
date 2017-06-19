#include <stdlib.h>

int array3D[2][3][2] = { { { 0, 1 }, { 2, 3 }, { 3, 4 } }, { { 5, 6 }, { 7, 8 }, { 9, 10 } } };

int main() {
  if (array3D[0][0][0] != 0) {
    abort();
  }
  if (array3D[0][0][1] != 1) {
    abort();
  }
  if (array3D[0][1][0] != 2) {
    abort();
  }
  if (array3D[0][1][1] != 3) {
    abort();
  }
  if (array3D[0][2][0] != 3) {
    abort();
  }
  if (array3D[0][2][1] != 4) {
    abort();
  }
  if (array3D[1][0][0] != 5) {
    abort();
  }
  if (array3D[1][0][1] != 6) {
    abort();
  }
  if (array3D[1][1][0] != 7) {
    abort();
  }
  if (array3D[1][1][1] != 8) {
    abort();
  }
  if (array3D[1][2][0] != 9) {
    abort();
  }
  if (array3D[1][2][1] != 10) {
    abort();
  }
}
