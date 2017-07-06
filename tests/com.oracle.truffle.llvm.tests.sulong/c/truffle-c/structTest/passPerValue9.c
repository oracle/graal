struct point {
  int x;
  int y;
};

struct test {
  char a[3];
  struct point* p;
};

int func(struct test t) {
  int sum = 0;
  sum += t.a[0];
  sum += t.a[1];
  sum += t.a[2];
  sum += t.p->x;
  sum += t.p->y;
  t.a[0] = 0;
  t.a[1] = 0;
  t.a[2] = 0;
  t.p->x = 0;
  t.p->y = 0;
  return sum;
}

int main() {
  struct test t = { { 1, 2, 3 }, &(struct point){4, 5} };
  int ret = func(t) + func(t);
  return ret + t.a[0] + t.a[1] + t.a[2] + t.p->x + t.p->y;
}
