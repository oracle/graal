#include <stdlib.h>

int array3D[2][3][2] = { { { 0, 1 }, { 2, 3 }, { 4, 5 } }, { { 6, 7 }, { 8, 9 }, { 10, 11 } } };

int main() {
  for (int i = 0; i < 2; ++i) {
    for (int j = 0; j < 3; ++j) {
      for (int k = 0; k < 2; ++k) {
        if (array3D[i][j][k] != (i * 6 + j * 2 + k)) {
          abort();
        }
      }
    }
  }
}
