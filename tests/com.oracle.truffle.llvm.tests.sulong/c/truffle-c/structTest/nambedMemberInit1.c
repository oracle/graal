struct test {
  int x;
};

int main() {
  struct test t = {.x = 3 };
  return t.x;
}
