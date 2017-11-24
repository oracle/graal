
enum type { SINGLE, DOUBLE, TRIPLE };

union val {
  int single_val;
  int double_val[2];
  int triple_val[3];
};

struct test {
  enum type t;
  union val val;
};

int main() {
  struct test t;
  t.t = DOUBLE;
  t.val.triple_val[0] = 1;
  t.val.triple_val[1] = 2;
  t.val.triple_val[2] = 3;
  if (t.t == SINGLE) {
    return t.val.single_val;
  } else if (t.t == DOUBLE) {
    return t.val.double_val[0] + t.val.double_val[1];
  } else {
    return t.val.triple_val[0] + t.val.triple_val[1] + t.val.triple_val[2];
  }
}
