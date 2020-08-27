//CPP
#include<polyglot.h>
#include<stdlib.h>
#include<iostream>



class A {
	public:
		int k;
		virtual int foo(int x);
};

class B {
	public:
		int k;
		int foo(int x);
		B();
};

int A::foo(int x) {return 0;} //dummy
int B::foo(int x) {return k+x;}

B::B() {k=1;}

POLYGLOT_DECLARE_CLASS(A);
POLYGLOT_DECLARE_CLASS(B);

A* getAByCreatingB() {
	void* polyglotB = polyglot_from_B(new B());
	A* a = polyglot_as_A(polyglotB);
	return a;
}

int evaluate(int x) {
	A* a = getAByCreatingB();
	return a->foo(x);
}

//------------------------------------------test native
class B1 {
	public:
	virtual int f();
	int g();
};

class B2: public B1 {
	public:
	int f() override;
	int g();
};

int B1::f() {return 0;}
int B2::f() {return 2;}
int B1::g() {return 0;}
int B2::g() {return 2;}

POLYGLOT_DECLARE_CLASS(B1);

void* getB1() {
	B1* b2 = new B2();
	return polyglot_from_B1(b2);
}

int getB1F() {
	B1* b2 = new B2();
	return b2->f();
}

int getB1G() {
	B1* b2 = new B2();
	return b2->g();
}
