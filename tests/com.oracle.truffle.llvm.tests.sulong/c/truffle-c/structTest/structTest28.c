struct test {
  int arr1[2];
  char arr2[3];
  int arr3[1];
};

int main() {
  struct test t = { { 1, 2 }, { 'a', 'b', 'c' }, { -2 } };
  return t.arr1[0] + t.arr1[1] + t.arr2[2] - t.arr2[0] - t.arr3[0];
}
