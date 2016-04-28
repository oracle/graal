typedef unsigned int V2SI __attribute__((vector_size(16)));

int main() {
  V2SI test1 = { -1, 8, 2, -5 };
  V2SI test2 = { 2, 4, 3, 7 };
  test1[2] = 10;
  V2SI result = test1 + test2;
  return result[0] + result[1];
}
