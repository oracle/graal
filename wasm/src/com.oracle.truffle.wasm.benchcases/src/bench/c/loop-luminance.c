
#include <stdlib.h>
#include <stdint.h>
#include "harness.h"

#define IMAGE_SIZE (1024 * 1024)
#define N 256
#define ITERATIONS (IMAGE_SIZE * N)

uint32_t image[IMAGE_SIZE];

int benchmarkWarmupCount() {
  return 10;
}

void benchmarkSetupOnce() {
  for (uint32_t i = 0; i != IMAGE_SIZE; ++i) {
    uint32_t value;
    value |= (((i + 1) * 8) % 128) << 24;
    value |= (((i + 1) * 16) % 128) << 16;
    value |= (((i + 1) * 24) % 128) << 8;
    image[i] = value;
  }
}

int benchmarkRun() {
  double total_luminance;
  total_luminance = 0.0;
  for (uint32_t pixel = 0; pixel != ITERATIONS; ++pixel) {
    uint32_t color = image[pixel % IMAGE_SIZE];
    uint8_t R = (color & 0xFF000000) >> 24;
    uint8_t G = (color & 0x00FF0000) >> 16;
    uint8_t B = (color & 0x0000FF00) >> 8;
    total_luminance += (0.2126*R + 0.7152*G + 0.0722*B);
  }
  return total_luminance / (double) N;
}
