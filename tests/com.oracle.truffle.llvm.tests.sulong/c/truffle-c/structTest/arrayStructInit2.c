struct test {
  int a;
  char b;
  int c[3];
};

int main() {
  struct test structs[] = { {.a = 1, .b = 'a', .c = { 2, 4, 8 } }, {.a = 2, .c = { 16 } }, {.b = 'b' } };
  int sum = 0;
  int i;
  for (i = 0; i < 3; i++) {
    sum += -structs[i].a + structs[i].b - structs[i].c[0] - structs[i].c[1] - structs[i].c[2];
  }
  return sum;
}
