struct test {
  int a;
};

int main() {
  struct test structs[] = { {.a = 1 }, {.a = 2 } };
  return structs[0].a + structs[1].a;
}
