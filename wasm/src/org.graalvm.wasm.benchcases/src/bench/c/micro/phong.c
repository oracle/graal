/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <math.h>
#include <stdint.h>
#include "harness.h"

#define WIDTH (800)
#define HEIGHT (600)
#define DEPTH (3)
#define DATA_SIZE (WIDTH * HEIGHT * DEPTH)
#define MESH_SIDE_LENGTH (60)
#define MESH_TRIANGLE_COUNT (4 * 2 * MESH_SIDE_LENGTH * MESH_SIDE_LENGTH)
#define MATRIX_N (4)
#define PI (3.141592654)

int hash = 0;

typedef struct {
  int8_t bfType[2];
  int32_t bfSize;
  int16_t bfReserved1;
  int16_t bfReserved2;
  int32_t bfOffBits;
} __attribute__((packed)) FileHeader;

typedef struct {
  int32_t biSize;
  int32_t biWidth;
  int32_t biHeight;
  int16_t biPlanes;
  int16_t biBitCount;
  int32_t biCompression;
  int32_t biSizeImage;
  int32_t biXPelsPerMeter;
  int32_t biYPelsPerMeter;
  int32_t biClrUsed;
  int32_t biClrImportant;
} __attribute__((packed)) ImageHeader;

typedef struct {
  FileHeader fileHeader;
  ImageHeader imageHeader;
  int8_t data[DATA_SIZE];
} __attribute__((packed)) Bitmap;

void initializeBitmap(Bitmap* bmp) {
  bmp->fileHeader.bfType[0] = 'B';
  bmp->fileHeader.bfType[1] = 'M';
  bmp->fileHeader.bfSize = sizeof(Bitmap);
  bmp->fileHeader.bfReserved1 = 0;
  bmp->fileHeader.bfReserved2 = 0;
  bmp->fileHeader.bfOffBits = sizeof(FileHeader) + sizeof(ImageHeader);
  bmp->imageHeader.biSize = sizeof(ImageHeader);
  bmp->imageHeader.biWidth = WIDTH;
  bmp->imageHeader.biHeight = HEIGHT;
  bmp->imageHeader.biPlanes = 1;
  bmp->imageHeader.biBitCount = 24;
  bmp->imageHeader.biCompression = 0;
  bmp->imageHeader.biSizeImage = 0;
  bmp->imageHeader.biXPelsPerMeter = 0;
  bmp->imageHeader.biYPelsPerMeter = 0;
  bmp->imageHeader.biClrUsed = 0;
  bmp->imageHeader.biClrImportant = 0;
}

Bitmap outputBitmap;
double zbuffer[WIDTH * HEIGHT];

typedef struct {
  double x;
  double y;
  double z;
} vec3;

void v_add(vec3* a, vec3* b, vec3* result) {
  result->x = a->x + b->x;
  result->y = a->y + b->y;
  result->z = a->z + b->z;
}

void v_sub(vec3* a, vec3* b, vec3* result) {
  result->x = a->x - b->x;
  result->y = a->y - b->y;
  result->z = a->z - b->z;
}

void v_smult(vec3* a, double k, vec3* result) {
  result->x = k * a->x;
  result->y = k * a->y;
  result->z = k * a->z;
}

double v_sprod(vec3* a, vec3* b) {
  return a->x * b->x + a->y * b->y + a->z * b->z;
}

void v_vprod(vec3* a, vec3* b, vec3* result) {
  double x = a->y * b->z - a->z * b->y;
  double y = -a->x * b->z + a->z * b->x;
  double z = a->x * b->y - a->y * b->x;
  result->x = x;
  result->y = y;
  result->z = z;
}

void v_hprod(vec3* a, vec3* b, vec3* result) {
  result->x = a->x * b->x;
  result->y = a->y * b->y;
  result->z = a->z * b->z;
}

double v_length(vec3* a) {
  double x = a->x;
  double y = a->y;
  double z = a->z;
  return sqrt(x * x + y * y + z * z);
}

void v_normalize(vec3* a, vec3* result) {
  double length = v_length(a);
  v_smult(a, 1.0 / length, result);
}

typedef struct {
  double v[MATRIX_N];
} vec4;

typedef struct {
  double v[MATRIX_N * MATRIX_N];
} matrix;

// Aliasing of argument pointers is not allowed.
void m_multiply(matrix *a, matrix *b, matrix *result) {
  for (int row = 0; row < MATRIX_N; row++) {
    for (int col = 0; col < MATRIX_N; col++) {
      result->v[row * MATRIX_N + col] = 0.0;
      for (int i = 0; i < MATRIX_N; i++) {
        result->v[row * MATRIX_N + col] += a->v[row * MATRIX_N + i] * b->v[i * MATRIX_N + col];
      }
    }
  }
}

// Aliasing of argument pointers is not allowed.
void m_vmultiply(matrix *a, vec4 *v, vec4 *result) {
  for (int row = 0; row < MATRIX_N; row++) {
    result->v[row] = 0.0;
    for (int col = 0; col < MATRIX_N; col++) {
      result->v[row] += a->v[row * MATRIX_N + col] * v->v[col];
    }
  }
}

matrix m_identity() {
  matrix m;
  for (int i = 0; i < MATRIX_N * MATRIX_N; i++) {
    m.v[i] = 0.0;
  }
  for (int i = 0; i < MATRIX_N; i++) {
    m.v[i * MATRIX_N + i] = 1.0;
  }
  return m;
}

int m_invert(matrix* input, matrix* result) {
    double* m = input->v;
    double tmp[MATRIX_N * MATRIX_N];

    tmp[0] = m[5]  * m[10] * m[15] -
      m[5]  * m[11] * m[14] -
      m[9]  * m[6]  * m[15] +
      m[9]  * m[7]  * m[14] +
      m[13] * m[6]  * m[11] -
      m[13] * m[7]  * m[10];
    tmp[4] = -m[4]  * m[10] * m[15] +
      m[4]  * m[11] * m[14] +
      m[8]  * m[6]  * m[15] -
      m[8]  * m[7]  * m[14] -
      m[12] * m[6]  * m[11] +
      m[12] * m[7]  * m[10];
    tmp[8] = m[4]  * m[9] * m[15] -
      m[4]  * m[11] * m[13] -
      m[8]  * m[5] * m[15] +
      m[8]  * m[7] * m[13] +
      m[12] * m[5] * m[11] -
      m[12] * m[7] * m[9];
    tmp[12] = -m[4]  * m[9] * m[14] +
      m[4]  * m[10] * m[13] +
      m[8]  * m[5] * m[14] -
      m[8]  * m[6] * m[13] -
      m[12] * m[5] * m[10] +
      m[12] * m[6] * m[9];
    tmp[1] = -m[1]  * m[10] * m[15] +
      m[1]  * m[11] * m[14] +
      m[9]  * m[2] * m[15] -
      m[9]  * m[3] * m[14] -
      m[13] * m[2] * m[11] +
      m[13] * m[3] * m[10];
    tmp[5] = m[0]  * m[10] * m[15] -
      m[0]  * m[11] * m[14] -
      m[8]  * m[2] * m[15] +
      m[8]  * m[3] * m[14] +
      m[12] * m[2] * m[11] -
      m[12] * m[3] * m[10];
    tmp[9] = -m[0]  * m[9] * m[15] +
      m[0]  * m[11] * m[13] +
      m[8]  * m[1] * m[15] -
      m[8]  * m[3] * m[13] -
      m[12] * m[1] * m[11] +
      m[12] * m[3] * m[9];
    tmp[13] = m[0]  * m[9] * m[14] -
      m[0]  * m[10] * m[13] -
      m[8]  * m[1] * m[14] +
      m[8]  * m[2] * m[13] +
      m[12] * m[1] * m[10] -
      m[12] * m[2] * m[9];
    tmp[2] = m[1]  * m[6] * m[15] -
      m[1]  * m[7] * m[14] -
      m[5]  * m[2] * m[15] +
      m[5]  * m[3] * m[14] +
      m[13] * m[2] * m[7] -
      m[13] * m[3] * m[6];
    tmp[6] = -m[0]  * m[6] * m[15] +
      m[0]  * m[7] * m[14] +
      m[4]  * m[2] * m[15] -
      m[4]  * m[3] * m[14] -
      m[12] * m[2] * m[7] +
      m[12] * m[3] * m[6];
    tmp[10] = m[0]  * m[5] * m[15] -
      m[0]  * m[7] * m[13] -
      m[4]  * m[1] * m[15] +
      m[4]  * m[3] * m[13] +
      m[12] * m[1] * m[7] -
      m[12] * m[3] * m[5];
    tmp[14] = -m[0]  * m[5] * m[14] +
      m[0]  * m[6] * m[13] +
      m[4]  * m[1] * m[14] -
      m[4]  * m[2] * m[13] -
      m[12] * m[1] * m[6] +
      m[12] * m[2] * m[5];
    tmp[3] = -m[1] * m[6] * m[11] +
      m[1] * m[7] * m[10] +
      m[5] * m[2] * m[11] -
      m[5] * m[3] * m[10] -
      m[9] * m[2] * m[7] +
      m[9] * m[3] * m[6];
    tmp[7] = m[0] * m[6] * m[11] -
      m[0] * m[7] * m[10] -
      m[4] * m[2] * m[11] +
      m[4] * m[3] * m[10] +
      m[8] * m[2] * m[7] -
      m[8] * m[3] * m[6];
    tmp[11] = -m[0] * m[5] * m[11] +
      m[0] * m[7] * m[9] +
      m[4] * m[1] * m[11] -
      m[4] * m[3] * m[9] -
      m[8] * m[1] * m[7] +
      m[8] * m[3] * m[5];
    tmp[15] = m[0] * m[5] * m[10] -
      m[0] * m[6] * m[9] -
      m[4] * m[1] * m[10] +
      m[4] * m[2] * m[9] +
      m[8] * m[1] * m[6] -
      m[8] * m[2] * m[5];

    double determinant = m[0] * tmp[0] + m[1] * tmp[4] + m[2] * tmp[8] + m[3] * tmp[12];

    if (determinant == 0) {
      return 1;
    }

    determinant = 1.0 / determinant;

    for (int i = 0; i < MATRIX_N * MATRIX_N; i++) {
      result->v[i] = tmp[i] * determinant;
    }

    return 0;
}

matrix m_translate(double x, double y, double z) {
  matrix m = {
    .v = {
      1.0, 0.0, 0.0, x,
      0.0, 1.0, 0.0, y,
      0.0, 0.0, 1.0, x,
      0.0, 0.0, 0.0, 1.0
    }
  };
  return m;
}

matrix m_scale(double fac) {
  matrix m = {
    .v = {
      fac, 0.0, 0.0, 0.0,
      0.0, fac, 0.0, 0.0,
      0.0, 0.0, fac, 0.0,
      0.0, 0.0, 0.0, 1.0
    }
  };
  return m;
}

matrix m_rotate(vec3* axis, double angle) {
  double x = axis->x;
  double y = axis->y;
  double z = axis->z;
  double s = sin(angle);
  double c = cos(angle);
  matrix m = {
    .v = {
      x * x * (1 - c) + 1 * c, x * y * (1 - c) - z * s, x * z * (1 - c) + y * s, 0.0,
      x * y * (1 - c) + z * s, y * y * (1 - c) + 1 * c, y * z * (1 - c) - x * s, 0.0,
      x * z * (1 - c) - y * s, y * z * (1 - c) + x * s, z * z * (1 - c) + 1 * c, 0.0,
      0.0, 0.0, 0.0, 1.0
    }
  };
  return m;
}

vec4 to_vec4(vec3* v) {
  vec4 result = {
    { v->x, v->y, v->z, 1.0 }
  };
  return result;
}

vec3 to_vec3(vec4* v) {
  vec3 result = {
    .x = v->v[0] / v->v[3],
    .y = v->v[1] / v->v[3],
    .z = v->v[2] / v->v[3]
  };
  return result;
}

typedef struct {
  vec3 pos;
  vec3 normal;
} vertex;

typedef struct {
  vertex a;
  vertex b;
  vertex c;
} triangle;

triangle mesh[MESH_TRIANGLE_COUNT];

double hill(int x, int y, double xc, double yc, double elevation, double denivelation) {
  double xp = x - xc;
  double yp = y - yc;
  return elevation / (1.0 + (xp * xp + yp * yp) / denivelation);
}

double turbulence(int x, int y, int seed, double strength, double frequency) {
  double offset =
    sin((x - seed) * frequency / PI) +
    sin((y - seed * 23) * frequency / PI) +
    sin((x + y - seed * 19) * frequency / PI) +
    sin((2 * x + y - seed * 21) * frequency / PI) +
    sin((x + 2 * y - seed * 14) * frequency / PI) +
    sin((3 * x + y - seed * 11) * frequency / PI) +
    sin((x + 3 * y - seed * 7) * frequency / PI) +
    sin((7 * x + 8 * y - seed * 7) * frequency / PI);
  offset /= 8;
  return strength * offset;
}

double height_at(int x, int y) {
   double h0 = hill(x, y, 0.0, 0.0, 5.0, 32.0);
   double h1 = hill(x, y, 0.0, 0.0, 8.0, 12.0);
   double h2 = hill(x, y, -10.0, -16.0, 3.5, 24.0);
   double h3 = hill(x, y, -10.0, -16.0, 8.5, 9.0);
   double h4 = hill(x, y, -10.0, 16.0, 6.5, 24.0);
   double h5 = hill(x, y, -9.0, 19.0, 4.6, 18.0);
   double h6 = hill(x, y, 14.0, -9.0, 6.2, 16.0);
   double h7 = hill(x, y, 37.0, 2.0, 5.7, 11.0);
   double h8 = hill(x, y, 21.0, -19.0, 6.7, 31.0);
   double h9 = hill(x, y, -1.0, 9.0, 5.1, 12.0);
   double h10 = hill(x, y, 6.0, 8.0, -2.0, 25.0);
   double h11 = hill(x, y, 16.0, 19.0, 6.2, 25.0);
   double h12 = hill(x, y, 22.0, 43.0, -4.1, 36.0);
   double hills = h0 + h1 + h2 + h3 + h4 + h5 + h6 + h7 + h8 + h9 + h10 + h11 + h12;
   double t0 = turbulence(x, y, 17, 1.78, 0.04);
   double t1 = turbulence(x, y, 19, 1.19, 0.15);
   double t2 = turbulence(x, y, 41, 0.91, 0.41);
   double t3 = turbulence(x, y, 91, 0.39, 0.97);
   double t4 = turbulence(x, y, 29, 0.29, 1.28);
   double t5 = turbulence(x, y, 29, 0.16, 2.41);
   double t6 = turbulence(x, y, 31, 0.11, 4.43);
   double t7 = turbulence(x, y, 37, 0.06, 6.12);
   double t8 = turbulence(x, y, 94, 0.03, 9.46);
   double ts = t0 + t1 + t2 + t3 + t4 + t5 + t6 + t7 + t8;
   double w0 = turbulence(x, y, 31, 3.11, 0.041);
   double w1 = turbulence(x, y, 11, 4.74, 0.057);
   double w2 = turbulence(x, y, 47, 5.14, 0.039);
   double waves = w0 + w1 + w2;
   return hills + ts + waves;
}

vec3 normal_at(int x, int y) {
  vec3 n;
  vec3 v0 = { .x = x - 1, .y = y - 1, .z = height_at(x - 1, y - 1) };
  vec3 v1 = { .x = x - 1, .y = y + 1, .z = height_at(x - 1, y + 1) };
  vec3 v2 = { .x = x + 1, .y = y - 1, .z = height_at(x + 1, y - 1) };
  vec3 d0;
  v_sub(&v1, &v0, &d0);
  vec3 d1;
  v_sub(&v2, &v0, &d1);
  v_vprod(&d1, &d0, &n);
  v_normalize(&n, &n);
  return n;
}

void initializeMesh() {
  int triangleCount = 0;
  for (int x = -MESH_SIDE_LENGTH; x < MESH_SIDE_LENGTH; x++) {
    for (int y = -MESH_SIDE_LENGTH; y < MESH_SIDE_LENGTH; y++) {
      vec3 v0 = { .x = x, .y = y, .z = height_at(x, y) };
      vec3 n0 = normal_at(x, y);
      vec3 v1 = { .x = x + 1, .y = y, .z = height_at(x + 1, y) };
      vec3 n1 = normal_at(x + 1, y);
      vec3 v2 = { .x = x + 1, .y = y + 1, .z = height_at(x + 1, y + 1) };
      vec3 n2 = normal_at(x + 1, y + 1);
      vec3 v3 = { .x = x, .y = y + 1, .z = height_at(x, y + 1) };
      vec3 n3 = normal_at(x, y + 1);
      triangle t0 = {
        .a = {
          .pos = v0,
          .normal = n0,
        },
        .b = {
          .pos = v1,
          .normal = n1,
        },
        .c = {
          .pos = v3,
          .normal = n3,
        }
      };
      triangle t1 = {
        .a = {
          .pos = v1,
          .normal = n1,
        },
        .b = {
          .pos = v2,
          .normal = n2,
        },
        .c = {
          .pos = v3,
          .normal = n3,
        }
      };
      mesh[triangleCount + 0] = t0;
      mesh[triangleCount + 1] = t1;
      triangleCount += 2;
    }
  }
}

void vertex_to_screen(matrix* xform, vec3* v, vec3* sv) {
  vec4 v4 = to_vec4(v);
  vec4 sv4;
  m_vmultiply(xform, &v4, &sv4);
  *sv = to_vec3(&sv4);
}

void triangle_to_screen(matrix* xform, triangle* t, vec3* a, vec3* b, vec3* c) {
  vertex_to_screen(xform, &(t->a.pos), a);
  vertex_to_screen(xform, &(t->b.pos), b);
  vertex_to_screen(xform, &(t->c.pos), c);
}

void write_pixel(int x, int y, double z, int r, int g, int b) {
  hash ^= x;
  hash ^= y;
  hash ^= r;
  hash ^= g;
  hash ^= b;

  if (x < 0 || x >= WIDTH) {
    return;
  }
  if (y < 0 || y >= HEIGHT) {
    return;
  }

  // Check and update z-buffer.
  int index = y * WIDTH + x;
  double epsilon = 0.01;
  if (z >= zbuffer[index] - epsilon) {
    return;
  }
  zbuffer[index] = z;

  // Emit pixel.
  int address = index * 3;
  outputBitmap.data[address + 0] = (int8_t) r;
  outputBitmap.data[address + 1] = (int8_t) g;
  outputBitmap.data[address + 2] = (int8_t) b;
}

void sort_points(int* order, vec3* a, vec3* b, vec3* c) {
  if (a->y < b->y) {
    if (a->y < c->y) {
      order[0] = 0;
      if (b->y < c->y) {
        order[1] = 1;
        order[2] = 2;
      } else {
        order[1] = 2;
        order[2] = 1;
      }
    } else {
      order[0] = 2;
      order[1] = 0;
      order[2] = 1;
    }
  } else {
    if (b->y < c->y) {
      order[0] = 1;
      if (a->y < c->y) {
        order[1] = 0;
        order[2] = 2;
      } else {
        order[1] = 2;
        order[2] = 0;
      }
    } else {
      order[0] = 2;
      order[1] = 1;
      order[2] = 0;
    }
  }
}

int iclamp(double x, double left, double right) {
  if (x < left) {
    return (int) left;
  } else if (x > right) {
    return (int) right;
  } else {
    return (int) x;
  }
}

double clamp(double x, double left, double right) {
  if (x < left) {
    return left;
  } else if (x > right) {
    return right;
  } else {
    return x;
  }
}

void v_interpolate_barycentric(
  vec3 weights, vec3* v0, vec3* v1, vec3* v2, vec3* result
) {
  double w0 = weights.x;
  double w1 = weights.y;
  double w2 = weights.z;
  result->x = 0.0;
  result->y = 0.0;
  result->z = 0.0;
  vec3 tmp = { .x = 0, .y = 0, .z = 0 };
  v_smult(v0, w0, &tmp);
  v_add(result, &tmp, result);
  v_smult(v1, w1, &tmp);
  v_add(result, &tmp, result);
  v_smult(v2, w2, &tmp);
  v_add(result, &tmp, result);
}

vec3 v_screen_barycentric(vec3* s0, vec3* s1, vec3* s2, vec3* p) {
  double x0 = s0->x;
  double y0 = s0->y;
  double x1 = s1->x;
  double y1 = s1->y;
  double x2 = s2->x;
  double y2 = s2->y;
  double xp = p->x;
  double yp = p->y;
  double w0 =
    ((y1 - y2) * (xp - x2) + (x2 - x1) * (yp - y2)) /
    ((y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2));
  double w1 =
    ((y2 - y0) * (xp - x2) + (x0 - x2) * (yp - y2)) /
    ((y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2));
  double w2 = 1 - w1 - w0;
  vec3 weights = { .x = w0, .y = w1, .z = w2 };
  return weights;
}

vec3 v_interpolate(vec3* v0, vec3* v1, double t) {
  vec3 result = { .x = 0, .y = 0, .z = 0 };
  vec3 tmp;
  v_smult(v0, 1.0 - t, &tmp);
  v_add(&result, &tmp, &result);
  v_smult(v1, t, &tmp);
  v_add(&result, &tmp, &result);
  return result;
}

vec3 v_noise(double xp, double yp) {
  int x = (int) (xp * 145379);
  int y = (int) (yp * 129731);
  int random = ((x * y) % 4187);
  if (random < 0) {
    random = -random;
  }
  double i = 0.0;
  i += random;
  i /= 4187.0;
  vec3 noise = { .x = i, .y = i, .z = i };
  return noise;
}

void draw_line(
  int xl, int xr, int y,
  vec3* s0, vec3* s1, vec3* s2,
  vec3* v0, vec3* v1, vec3* v2,
  vec3* n0, vec3* n1, vec3* n2,
  vec3* lightPos
) {
  vec3 normal;
  vec3 position;
  vec3 lightDir;
  vec3 loColor = { .x = 0.2, .y = 1.0, .z = 0.3 };
  vec3 hiColor = { .x = 0.3, .y = 0.7, .z = 0.9 };
  double loHeight = 0.2;
  double hiHeight = 3.1;
  for (int x = xl; x <= xr; x++) {
    // Compute barycentric coordinates.
    vec3 p = { .x = x, .y = y, .z = 0.0 };
    vec3 weights = v_screen_barycentric(s0, s1, s2, &p);

    // Interpolate the normal.
    v_interpolate_barycentric(weights, n0, n1, n2, &normal);
    v_normalize(&normal, &normal);

    // Interpolate the position.
    v_interpolate_barycentric(weights, v0, v1, v2, &position);

    // Compute the light vector.
    v_sub(lightPos, &position, &lightDir);
    v_normalize(&lightDir, &lightDir);

    // Compute intensity.
    double reflection = v_sprod(&normal, &lightDir);
    double intensity = 0.20 + 0.55 * reflection;
    if (intensity < 0.0) {
      intensity = 0.0;
    }

    // Compute color.
    double height = position.z;
    double heightIndex = clamp((height - loHeight) / (hiHeight - loHeight), 0.0, 1.0);
    vec3 originalColor = v_interpolate(&loColor, &hiColor, heightIndex);
    vec3 noise = v_noise(position.x, position.y);
    vec3 noiseColor;
    v_hprod(&originalColor, &noise, &noiseColor);
    vec3 color = v_interpolate(&originalColor, &noiseColor, 0.1);

    // Interpolate the z-value, and emit the pixel.
    double z = weights.x * s0->z + weights.y * s1->z + weights.z * s2->z;
    write_pixel(
      x, y, z,
      iclamp(255 * intensity * color.x, 0.0, 255.0),
      iclamp(255 * intensity * color.y, 0.0, 255.0),
      iclamp(255 * intensity * color.z, 0.0, 255.0)
    );
  }
}

void rasterize_half(
  double* x1, double* x2, int* y, int yuntil,
  double bl, double br,
  double d1, double d2,
  vec3* s0, vec3* s1, vec3* s2,
  vec3* v0, vec3* v1, vec3* v2,
  vec3* n0, vec3* n1, vec3* n2,
  vec3* lightPos
) {
  while (*y < yuntil) {
    // Add differentials to screen coordinates.
    *x1 += d1;
    *x2 += d2;
    if (*x1 < *x2) {
      draw_line(
        iclamp(*x1, bl, br), iclamp(*x2, bl, br), *y,
        s0, s1, s2,
        v0, v1, v2,
        n0, n1, n2,
        lightPos);
    } else {
      draw_line(
        iclamp(*x2, bl, br), iclamp(*x1, bl, br), *y,
        s0, s1, s2,
        v0, v1, v2,
        n0, n1, n2,
        lightPos);
    }
    (*y)++;
  }
}

void rasterize(
  vec3* a, vec3* b, vec3* c,
  vec3* va, vec3* vb, vec3* vc,
  vec3* na, vec3* nb, vec3* nc,
  vec3* lightPos
) {
  // Sort by y-coordinate.
  int order[3];
  sort_points(order, a, b, c);
  vec3* unordered[3];

  vec3 screen[3];
  unordered[0] = a;
  unordered[1] = b;
  unordered[2] = c;
  screen[0] = *unordered[order[0]];
  screen[1] = *unordered[order[1]];
  screen[2] = *unordered[order[2]];
  vec3* vertices[3];
  unordered[0] = va;
  unordered[1] = vb;
  unordered[2] = vc;
  vertices[0] = unordered[order[0]];
  vertices[1] = unordered[order[1]];
  vertices[2] = unordered[order[2]];
  vec3* normals[3];
  unordered[0] = na;
  unordered[1] = nb;
  unordered[2] = nc;
  normals[0] = unordered[order[0]];
  normals[1] = unordered[order[1]];
  normals[2] = unordered[order[2]];

  // Find bounds.
  double bl;
  double br;
  if (a->x < b->x) {
    if (a->x < c->x) {
      bl = a->x;
      if (c->x < b->x) {
        br = b->x;
      } else {
        br = c->x;
      }
    } else {
      bl = c->x;
      br = b->x;
    }
  } else {
    if (b->x < c->x) {
      bl = b->x;
      if (c->x < b->x) {
        br = b->x;
      } else {
        br = c->x;
      }
    } else {
      bl = c->x;
      br = a->x;
    }
  }

  if (((int) screen[2].y) - ((int) screen[0].y) == 0.0) {
    // Completely thin polygon.
    return;
  }

  double d1 = (screen[2].x - screen[0].x) / (screen[2].y - screen[0].y);
  double d2 = (screen[1].x - screen[0].x) / (screen[1].y - screen[0].y);
  int y = (int) screen[0].y;
  double x1 = screen[0].x;
  double x2 = screen[0].x;
  if ((int) screen[1].y != (int) screen[0].y) {
    int yuntil = (int) screen[1].y;
    rasterize_half(&x1, &x2, &y, yuntil, bl, br, d1, d2,
      &screen[0], &screen[1], &screen[2],
      vertices[0], vertices[1], vertices[2],
      normals[0], normals[1], normals[2],
      lightPos);
  }
  x2 = screen[1].x;
  d2 = (screen[2].x - screen[1].x) / (screen[2].y - screen[1].y);
  if ((int) screen[2].y != (int) screen[1].y) {
    int yuntil = (int) screen[2].y;
    rasterize_half(&x1, &x2, &y, yuntil, bl, br, d1, d2,
      &screen[0], &screen[1], &screen[2],
      vertices[0], vertices[1], vertices[2],
      normals[0], normals[1], normals[2],
      lightPos);
  }

  // write_pixel((int) a->x, (int) a->y, 255, 255, 255);
  // write_pixel((int) b->x, (int) b->y, 255, 255, 255);
  // write_pixel((int) c->x, (int) c->y, 255, 255, 255);
}

int render() {
  // Initialize the MVP matrix.
  matrix id = m_identity();
  // First rotate.
  vec3 xyAxis = { .x = -0.707, .y = 0.707, .z = 0.0 };
  matrix rotate2 = m_rotate(&xyAxis, PI / 3);
  matrix tmp1;
  m_multiply(&id, &rotate2, &tmp1);
  // Second rotate.
  vec3 zAxis = { .x = 0.0, .y = 0.0, .z = 1.0 };
  matrix rotate1 = m_rotate(&zAxis, PI / 2 + PI / 6);
  matrix tmp2;
  m_multiply(&tmp1, &rotate1, &tmp2);
  // Scale.
  matrix scale = m_scale(1 / 15.0);
  matrix tmp3;
  m_multiply(&tmp2, &scale, &tmp3);
  // Translate.
  matrix translate = m_translate(-400.0, -400.0, 0.0);
  matrix modelview;
  m_multiply(&tmp3, &translate, &modelview);
  // Project.
  matrix projection = m_identity();
  matrix xform;
  m_multiply(&modelview, &projection, &xform);
  m_invert(&xform, &xform);

  // Light position.
  vec3 lightPos = {
    .x = -2000,
    .y = 2000,
    .z = 2000
  };

  // Reset the z-buffer.
  for (int i = 0; i < WIDTH * HEIGHT; i++) {
    zbuffer[i] = 1 << 30;
  }

  // Reset hash.
  hash = 0;

  // Traverse each triangle in the mesh.
  for (int i = 0; i < MESH_TRIANGLE_COUNT; i++) {
    triangle t = mesh[i];

    // Map the vertices to screen space.
    vec3 a;
    vec3 b;
    vec3 c;
    triangle_to_screen(&xform, &t, &a, &b, &c);

    // Rasterize the triangle.
    rasterize(
      &a, &b, &c,
      &t.a.pos, &t.b.pos, &t.c.pos,
      &t.a.normal, &t.b.normal, &t.c.normal,
      &lightPos);
  }

  return hash;
}

int benchmarkIterationsCount() {
  return 40;
}

void benchmarkSetupOnce() {
  initializeMesh();
}

void benchmarkSetupEach() {
  initializeBitmap(&outputBitmap);
}

void benchmarkTeardownEach(char* outputFile) {
  if(outputFile == NULL) {
    return;
  }

  FILE *filePointer = fopen(outputFile, "wb+");

  if (filePointer == NULL){
    printf("Error! opening file\n");
    exit(1);
  }

  fwrite(&outputBitmap, sizeof(Bitmap), 1, filePointer);
  printf("Wrote result bitmap to %s.\n", outputFile);
}

int benchmarkRun() {
  return render();
}
