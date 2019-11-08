
#include <stdlib.h>
#include <stdint.h>
#include "harness.h"

#define X 1024
#define Y 1024
#define IMAGE_SIZE (X * Y)
#define N 8
#define ITERATIONS (IMAGE_SIZE * N)

uint32_t image[X][Y];

int benchmarkWarmupCount() {
  return 10;
}

void benchmarkSetupOnce() {
  for (uint32_t i = 0; i != X; ++i) {
    for (uint32_t j = 0; j != Y; ++j) {
      uint32_t value = 0;
      value |= (((i + 1) * 8) % 128) << 24;
      value |= (((i + 1) * 16) % 128) << 16;
      value |= (((i + 1) * 24) % 128) << 8;
      image[i][j] = value;
    }
  }
}

int benchmarkRun() {
  uint32_t res = 0;
  for (uint32_t a = 0; a != X * N; ++a) {
    for (uint32_t b = 0; b != Y * N; ++b) {
      uint32_t r_sum = 0;
      uint32_t g_sum = 0;
      uint32_t b_sum = 0;

      uint32_t i = a % N;
      uint32_t j = b % N;

      uint32_t x_min = i > 0 ? i - 1 : i;
      uint32_t x_max = i < X - 1 ? i + 1 : i;
      uint32_t y_min = j > 0 ? j - 1 : j;
      uint32_t y_max = j < Y - 1 ? j + 1 : j;

      for (uint32_t ii = x_min; ii <= x_max; ++ii) {
        for (uint32_t jj = y_min; jj <= y_max; ++jj) {
          r_sum += (image[ii][jj] & 0xFF000000) >> 24;
          g_sum += (image[ii][jj] & 0x00FF0000) >> 16;
          b_sum += (image[ii][jj] & 0x0000FF00) >> 8;
        }
      }

      uint32_t num_pixels = (x_max - x_min + 1) * (y_max - y_min + 1);
      res = 0;
      res |= (r_sum / num_pixels) << 24;
      res |= (g_sum / num_pixels) << 16;
      res |= (b_sum / num_pixels) << 8;
      image[i][j] = res;
    }
  }
  return res / N;
}
