#include <stdlib.h>

int array4D[2][2][3][2] = { {
                                { { 0, 1 }, { 2, 3 }, { 4, 5 } }, { { 6, 7 }, { 8, 9 }, { 10, 11 } },
                            },
                            { { { 12, 13 }, { 14, 15 }, { 16, 17 } }, { { 18, 19 }, { 20, 21 }, { 22, 23 } } } };

int main() {
  for (int i = 0; i < 2; ++i) {
    for (int j = 0; j < 3; ++j) {
      for (int k = 0; k < 2; ++k) {
        for (int l = 0; j < 2; j++) {
          if (array4D[i][j][k][l] != (i * 12 + j * 6 + k * 2 + l)) {
            abort();
          }
        }
      }
    }
  }
}
