#include <cstdio>

class MyClass {
private:
    int a;
    float b;
    double c;
    long d;
    char e;
    short f[3];
public:
    MyClass(int _a, float _b, double _c, long _d, char _e, short f1, short f2, short f3) {
        this->a = _a;
        this->b = _b;
        this->c = _c;
        this->d = _d;
        this->e = _e;
        this->f[0] = f1;
        this->f[1] = f2;
        this->f[2] = f3;
    }

    void myMethod() {
    }
};

static void myStaticMethod(MyClass & myClass) {
}

#define MYCLASS_ARGS 16, 3.2f, 4.657, 149237354238697, 'e', -32768, -1, 32767

MyClass globalObj(MYCLASS_ARGS);
MyClass *globalPtr = new MyClass(MYCLASS_ARGS);

// set constructor priority to ensure 'start' is 
// not executed prior to the global initializers
int start() __attribute__((constructor (65536))) {
    MyClass localObj(MYCLASS_ARGS);
    MyClass *localPtr = new MyClass(MYCLASS_ARGS);

    localObj.myMethod();
    myStaticMethod(localObj);
    localPtr->myMethod();
    myStaticMethod(*localPtr);
    globalObj.myMethod();
    myStaticMethod(globalObj);
    globalPtr->myMethod();
    myStaticMethod(*globalPtr);

    return 0;
}
