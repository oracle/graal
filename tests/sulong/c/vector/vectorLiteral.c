typedef int V2SI __attribute__((vector_size(8)));

int main() {
  V2SI test = { -3, 4 };
  if (test[0] != -3 || test[1] != 4) {
    abort();
  }
}
