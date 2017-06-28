
class Base {
  protected:
    int x;
  public:
    void set_value (int a)
      { x=a; }
    virtual int foo ()
      { return 13; }
};

class A: public Base {
  public:
    int foo ()
      { return x * 2; }
};

class B: public Base {
  public:
    int foo ()
      { return (x * 3); }
};

int main () {
  A a;
  B b;
  Base c;
  Base * ppoly1 = &a;
  Base * ppoly2 = &b;
  Base * ppoly3 = &c;
  ppoly1->set_value (3);
  ppoly2->set_value (5);
  ppoly3->set_value (0);
  return ppoly1->foo() + ppoly2->foo() + ppoly3->foo();
}