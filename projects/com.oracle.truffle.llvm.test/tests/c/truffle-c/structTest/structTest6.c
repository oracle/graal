struct test {
  int a;
  int b;
  int c;
};

int main() {
  int size_struct = sizeof(struct test);
  int size_elements = sizeof(int) * 3;
  return size_struct >= size_elements;
}
