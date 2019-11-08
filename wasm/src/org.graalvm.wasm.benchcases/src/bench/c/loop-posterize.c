
#include <stdlib.h>
#include <stdint.h>
#include "harness.h"

#define IMAGE_SIZE (1024 * 1024)
#define N 256
#define ITERATIONS (IMAGE_SIZE * N)

uint32_t image[IMAGE_SIZE];
uint32_t result[IMAGE_SIZE];

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
  int black_pixels;
  black_pixels = 0;
  for (uint32_t pixel = 0; pixel != ITERATIONS; ++pixel) {
    uint32_t color = image[pixel % IMAGE_SIZE];
    uint8_t R = (color & 0xFF000000) >> 24;
    uint8_t G = (color & 0x00FF0000) >> 16;
    uint8_t B = (color & 0x0000FF00) >> 8;
    double luminance = (0.2126 * R + 0.7152 * G + 0.0722 * B);
    result[pixel % IMAGE_SIZE] = luminance > 127 ? UINT32_MAX : 0xFF;
    black_pixels += luminance > 127 ? 0 : 1;
  }
  return black_pixels / (double) ITERATIONS;
}
