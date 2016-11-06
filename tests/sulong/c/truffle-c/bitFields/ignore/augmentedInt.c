extern void abort(void);

struct test {
  int a : 3;
};

int main() {
  struct test t = { 0 };
  if ((t.a += 1) != 1) {
    abort();
  }
  if (t.a != 1) {
    abort();
  }
  if ((t.a += 2) != 3) {
    abort();
  }
  if (t.a != 3) {
    abort();
  }
  if ((t.a += 8) != 3) {
    abort();
  }
  if (t.a != 3) {
    abort();
  }
  if ((t.a -= 1) != 2) {
    abort();
  }
  if (t.a != 2) {
    abort();
  }
  if ((t.a -= 8) != 2) {
    abort();
  }
  if (t.a != 2) {
    abort();
  }
  if ((t.a *= 2) != -4) {
    abort();
  }
  if (t.a != -4) {
    abort();
  }
  if ((t.a *= 4) != 0) {
    abort();
  }
  if (t.a != 0) {
    abort();
  }
  if ((t.a &= 8) != 0) {
    abort();
  }
  if (t.a != 0) {
    abort();
  }
  t.a += 4;
  if ((t.a &= 4) != -4) {
    abort();
  }
  if (t.a != -4) {
    abort();
  }
  if ((t.a &= 7) != -4) {
    abort();
  }
  if (t.a != -4) {
    abort();
  }
  if ((t.a |= 3) != -1) {
    abort();
  }
  if (t.a != -1) {
    abort();
  }
  t.a = 1;
  if ((t.a |= 3) != 3) {
    abort();
  }
  if (t.a != 3) {
    abort();
  }
  if ((t.a ^= 3) != 0) {
    abort();
  }
  if (t.a != 0) {
    abort();
  }
  if ((t.a ^= 3) != 3) {
    abort();
  }
  if (t.a != 3) {
    abort();
  }
  return 0;
}
