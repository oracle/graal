struct test {
  int a;
  int b[3];
  char c;
};

int func(struct test t) {
  int sum = 0;
  sum += t.a;
  sum += t.b[0];
  sum += t.b[1];
  sum += t.b[2];
  // sum += t.c;
  t.a = 0;
  t.b[0] = 0;
  t.b[1] = 0;
  t.b[2] = 0;
  t.c = 0;
  return sum;
}

int main() {
  struct test t = { 1, { 4, 8, 16 }, 2 };
  int ret = func(t);
  return ret; // + t.a + t.b[0] + t.b[1] + t.b[2] + t.c;
}

/* struct test {
        int a;
        int b[3];
        char c;
};

int func(struct test t) {
        int sum = 0;
        sum += t.a;
        sum += t.b[0];
        sum += t.b[1];
        sum += t.b[2];
        sum += t.c;
        t.a = 0;
        t.b[0] = 0;
        t.b[1] = 0;
        t.b[2] = 0;
        t.c = 0;
        return sum;
}

int main() {
        struct test t = {1, {4, 8, 16}, 2};
        int ret = func(t) + func(t);
        return ret + t.a + t.b[0] + t.b[1] + t.b[2] + t.c;
}
* */
