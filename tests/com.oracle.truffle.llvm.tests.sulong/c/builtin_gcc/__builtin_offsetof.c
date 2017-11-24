struct point {
  short x;
  int y;
  long z;
};

struct __attribute__((__packed__)) pointPacked {
  short x;
  int y;
  long z;
};

int main() {
  if (__builtin_offsetof(struct point, x) != 0) {
    return 1;
  }
  if (__builtin_offsetof(struct point, y) != 4) {
    return 1;
  }
  if (__builtin_offsetof(struct point, z) != 8) {
    return 1;
  }
  if (__builtin_offsetof(struct pointPacked, x) != 0) {
    return 1;
  }
  if (__builtin_offsetof(struct pointPacked, y) != 2) {
    return 1;
  }
  if (__builtin_offsetof(struct pointPacked, z) != 6) {
    return 1;
  }
  return 0;
}
