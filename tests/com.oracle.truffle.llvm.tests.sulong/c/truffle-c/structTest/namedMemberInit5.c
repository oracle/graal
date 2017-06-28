enum asdf {
  val = 5
};

struct test {
  enum asdf t;
};

int main() {
  struct test t = { .t = val };
  return t.t;
}
