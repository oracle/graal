struct test {
  int w[10];
  int x;
  char y;
  long z;
};

int main() {
  struct test t = {.w = { 1, 32, 3, 45, 5, 6, 7, 8, 9, 10 }, .x = 3, .y = 'a', .z = (long)1 };
  int sum = 0;
  sum += t.w[1] + t.w[3] + t.w[9] + t.w[2] + t.w[5];
  sum += (int)(t.x - t.y + t.z);
  return sum;
}
