//CPP
#include<polyglot.h>
#include<stdlib.h>
#include<iostream>

//-----------------------------------------test via polyglot API

class A {
	public:
		int k;
		virtual int foo(int x);
};

int A::foo(int x) {return 0;} //dummy

POLYGLOT_DECLARE_CLASS(A);

int evaluateDirectly(A* a, int x) {
	return a->foo(x);
}

int evaluateWithPolyglotConversion(void* aObj, int x) {
	return evaluateDirectly(polyglot_as_A(aObj), x);
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

int getB1F() {
	B1* b2 = new B2();
	return b2->f();
}

int getB1G() {
	B1* b2 = new B2();
	return b2->g();
}
