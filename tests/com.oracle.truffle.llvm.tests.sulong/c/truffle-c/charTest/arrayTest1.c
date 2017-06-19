char arr[2] = { 1, 2 };

int main() {
  char *p;
  p = arr;
  return *(p + 1);
}
