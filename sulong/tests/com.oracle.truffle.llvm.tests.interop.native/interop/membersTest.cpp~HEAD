#include<stdio.h>
#include<stdlib.h>
#include<polyglot.h>

void hello() {
	printf("hello() is being called in testfile membersTest.cc");
}

void bye() {
	printf("bye() is being called in testfile membersTest.cc");
}

int gcd(int a, int b) {
	if(a==0) {return b;}
	else if(b==0) {return a;}
	else if(a<0) {return gcd(-a, b);}
	else if(b<0) {return gcd(a, -b);}
	else {return gcd(b, a%b);}
}

