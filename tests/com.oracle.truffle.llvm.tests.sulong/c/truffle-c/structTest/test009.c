struct test {
  int x;
  int y;
  int z;
};

int main() {
  struct test t = {.y = 3 };
  return t.y;
}
