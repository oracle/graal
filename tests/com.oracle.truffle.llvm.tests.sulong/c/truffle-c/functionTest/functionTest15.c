
int add(int first, int second) {
  first = first + 2;
  return first + second;
}

int main() { return add(1, add(3, add(4, 5))); }
