class Test {
public:
  int val;
  int test();
};

int Test::test() { return val; }

int main() {
  Test t;
  return t.test();
}
