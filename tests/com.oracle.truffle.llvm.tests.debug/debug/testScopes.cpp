#include <stdio.h>
#include <stdlib.h>

namespace MyNamespace {

    int nextID = 72;

    int getNextId()
    {
        int result = nextID++;
        return result;
    }
};

using namespace MyNamespace;

int globalX = 512;

class MyClass
{

private:

    static int lastId;
    int id;

public:

    MyClass() {
        id = getNextId();
        lastId = id;
        printf("MyClass Constructor\n");
    }

    int getID() {
        return this->id;
    }

};

int MyClass::lastId = -1;

int getX() {
    int x = globalX++;
    return x;
}

int main() {
    int x = 0;
    printf("x = %d\n", x);
    x = getX();
    printf("x = %d\n", x);
    {
        MyClass a;
        int x = a.getID();
        printf("x = %d\n", x);
    }
    printf("x = %d\n", x);

    return 0;
}
