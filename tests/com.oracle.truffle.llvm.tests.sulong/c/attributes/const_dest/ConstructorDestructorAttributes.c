#include <stdio.h>

// This test is an extended version of 
// SingleSource/Regression/C/ConstructorDestructorAttributes.c
// in the LLVMv3.2 Testsuite.

// the order of constructors with the same priorities is not
// specified and can vary between platforms and compilers

void ctor1() __attribute__((constructor (101)));

void ctor1() {
   printf("Create1!\n");
}

void ctor2() __attribute__((constructor (102)));

void ctor2() {
   printf("Create2!\n");
}

void ctor3() __attribute__((constructor (103)));

void ctor3() {
   printf("Create3!\n");
}

void ctor4() __attribute__((constructor (104)));

void ctor4() {
   printf("Create4!\n");
}

void dtor1() __attribute__((destructor (102)));

void dtor1() {
   printf("Destroy1!\n");
}

void dtor2() __attribute__((destructor (103)));

void dtor2() {
   printf("Destroy2!\n");
}

void dtor3() __attribute__((destructor (104)));

void dtor3() {
   printf("Destroy3!\n");
}

void dtor4() __attribute__((destructor (105)));

void dtor4() {
   printf("Destroy4!\n");
}

int main() { return 0; }
