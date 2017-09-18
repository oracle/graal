#include<stdio.h>
#include<stdlib.h>
#include <truffle.h>

class Test {

	public:
		void setA(long a);
		void setB(double b);
		void setC(float c);
		void setD(int d);
		void setE(unsigned char e);
		void setF(bool f);
		
		long getA(void);
		double getB(void);
		float getC(void);
		int getD(void);
		unsigned char getE(void);
		bool getF(void);

		void * operator new(size_t size) {
			return truffle_virtual_malloc(size);
		} 

		void operator delete(void * p) {
			// free(p);
		}

	private:
		long a;
		double b;
		float c;
		int d;
		unsigned char e;
		bool f;
};


void Test::setA(long v ) {
	a = v;
}

void Test::setB(double v ) {
	b = v;
}

void Test::setC(float v ) {
	c = v;
}

void Test::setD(int v ) {
	d = v;
}

void Test::setE(unsigned char v ) {
	e = v;
}

void Test::setF(bool v ) {
	f = v;
}


long Test::getA( void ) {
	return a;
}

double Test::getB( void ) {
	return b;
}

float Test::getC( void ) {
	return c;
}

int Test::getD( void ) {
	return d;
}

unsigned char Test::getE( void ) {
	return e;
}

bool Test::getF( void ) {
	return f;
}


// test functions
extern "C" 
long testGetA( void ) {
	Test* t = new Test();

	t->setA(42);
	t->setB(13.4);
	t->setC(13.5f);
	t->setD(56);
	t->setE(5);
	t->setF(true);

	return t->getA();
}

extern "C" 
double testGetB( void ) {
	Test* t = new Test();

	t->setA(42);
	t->setB(13.4);
	t->setC(13.5f);
	t->setD(56);
	t->setE(5);
	t->setF(true);

	return t->getB();
}

extern "C" 
float testGetC( void ) {
	Test* t = new Test();

	t->setA(42);
	t->setB(13.4);
	t->setC(13.5f);
	t->setD(56);
	t->setE(5);
	t->setF(true);

	return t->getC();
}

extern "C" 
int testGetD( void ) {
	Test* t = new Test();

	t->setA(42);
	t->setB(13.4);
	t->setC(13.5f);
	t->setD(56);
	t->setE(5);
	t->setF(true);

	return t->getD();
}

extern "C" 
unsigned char testGetE( void ) {
	Test* t = new Test();

	t->setA(42);
	t->setB(13.4);
	t->setC(13.5f);
	t->setD(56);
	t->setE(5);
	t->setF(true);

	return t->getE();
}

extern "C" 
bool testGetF( void ) {
	Test* t = new Test();

	t->setA(42);
	t->setB(13.4);
	t->setC(13.5f);
	t->setD(56);
	t->setE(5);
	t->setF(true);

	return t->getF();
}

