#include <stdio.h>

struct vec {
  float x;
  float y;
  float z;
  float w;
};

void to_pvector2(float x, float y, struct vec *result) {
  result->x = x;
  result->y = y;
  result->z = 0.0f;
  result->w = 1.0f;
}

struct vec __attribute__((noinline)) to_vector2(float x, float y) {
  struct vec result;
  to_pvector2(x, y, &result);
  return result;
}

int main() {
  struct vec a = to_vector2(2.0, 3.0);
  printf("%f %f\n", a.x, a.y);
}
