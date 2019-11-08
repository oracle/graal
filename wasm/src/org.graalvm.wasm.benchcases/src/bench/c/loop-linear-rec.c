
#include <stdlib.h>
#include <stdint.h>
#include "harness.h"

#define X 1024
#define Y 1024
#define IMAGE_SIZE (1024 * 1024)
#define N 512
#define ITERATIONS (IMAGE_SIZE * N)

uint32_t w[IMAGE_SIZE];
uint32_t b[X][Y];

int benchmarkWarmupCount() {
  return 10;
}

void benchmarkSetupOnce() {
  for (uint32_t i = 0; i != X; ++i) {
    uint32_t value = 0;
    value |= (((i + 1) * 8) % 128) << 24;
    value |= (((i + 1) * 16) % 128) << 16;
    value |= (((i + 1) * 24) % 128) << 8;
    w[i] = value;
    for (uint32_t j = 0; j != Y; ++j) {
      b[i][j] = -value;
    }
  }
}

int benchmarkRun() {
  uint32_t sum = 0;
  for (uint32_t l = 1; l <= N; l++) {
    for (uint32_t i = 1; i < Y; i++) {
      for (uint32_t k = 0; k < i; k++) {
        w[i] += b[k][i] * w[(i - k) - 1];
        sum += w[i];
      }
    }
  }
  return sum / X;
}
