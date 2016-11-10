class Test {
public:
  int val = 42;
  int test();
};

int Test::test() { return val; }

int main() {
  Test t;
  return t.test();
}
