#include <polyglot.h>

class A0 {
public:
    int a0;
};

class A1 : public A0 {
public:
    int a1;
};

class A2 : public A1 {
public:
    int a2;
};

class A3 : public A2 {
public:
    int a3;
};

class A4 : public A3 {
public:
    int a4;
};

POLYGLOT_DECLARE_TYPE(A0);
POLYGLOT_DECLARE_TYPE(A1);
POLYGLOT_DECLARE_TYPE(A2);
POLYGLOT_DECLARE_TYPE(A3);
POLYGLOT_DECLARE_TYPE(A4);

bool check0(void *a0Obj) {
    A0 *a0 = polyglot_as_A0(a0Obj);
    return a0->a0 == 0;
}

bool check1(void *a1Obj) {
    A1 *a1 = polyglot_as_A1(a1Obj);
    return (a1->a0 == 0) && (a1->a1 == 1);
}

bool check2(void *a2Obj) {
    A2 *a2 = polyglot_as_A2(a2Obj);
    return (a2->a0 == 0) && (a2->a1 == 1) && (a2->a2 == 2);
}

bool check3(void *a3Obj) {
    A3 *a3 = polyglot_as_A3(a3Obj);
    return (a3->a0 == 0) && (a3->a1 == 1) && (a3->a2 == 2) && (a3->a3 == 3);
}

bool check4(void *a4Obj) {
    A4 *a4 = polyglot_as_A4(a4Obj);
    return (a4->a0 == 0) && (a4->a1 == 1) && (a4->a2 == 2) && (a4->a3 == 3) && (a4->a4 == 4);
}
