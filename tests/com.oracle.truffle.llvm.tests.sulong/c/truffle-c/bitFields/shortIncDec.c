extern void abort(void);

struct test {
  short a : 2;
};

int main() {
  struct test t = { 0 };
  if (t.a++ != 0) {
    abort();
  }
  if (t.a != 1) {
    abort();
  }
  if (++t.a != -2) {
    abort();
  }
  if (t.a != -2) {
    abort();
  }
  if (t.a-- != -2) {
    abort();
  }
  if (t.a != 1) {
    abort();
  }
  if (--t.a != 0) {
    abort();
  }
  if (t.a != 0) {
    abort();
  }
  return 0;
}
