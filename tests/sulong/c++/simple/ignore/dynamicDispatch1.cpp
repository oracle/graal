// parser fails on this
class A {
public:
  virtual int fetchClassName() { return 1; }
};

class B : public A {
public:
  virtual int fetchClassName() { return 2; }
};

int main(void) {
  B obj_b;
  A &obj_a = obj_b;
  return obj_a.fetchClassName();
}
