int counter = 0;

int main();

int test() { return main(); }

int main() {
  counter++;
  if (counter == 1) {
    return 5;
  }
  return test();
}
