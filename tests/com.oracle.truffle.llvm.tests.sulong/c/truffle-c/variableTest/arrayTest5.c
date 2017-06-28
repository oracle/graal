int func(int foo[]) { return foo[1]; }

int main() {
  int test[] = { 7, 8, 9 };
  int result = func(test);
  return result;
}
