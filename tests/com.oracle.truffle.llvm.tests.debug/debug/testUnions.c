
union simpleUnion {
    int a;
    int b;
    int c;
};

// at O1, this is represented as a single float value
union floatUnion {
    float a;
    short b;
    short c;
    float d;
};

// at O1, this is represented as a single double value
union doubleUnion {
    float a;
    double b;
    int c;
    double d;
};

// at O1, this is represented as a single long value
union pointerUnion {
    short a;
    int b;
    int* c;
};

union simpleUnion myGlobalSimpleUnion;
union floatUnion myGlobalFloatUnion;
union doubleUnion myGlobalDoubleUnion;
union pointerUnion myGlobalPointerUnion;

int start() __attribute__((constructor))
{
    myGlobalSimpleUnion.a = 1 << 4;
    myGlobalSimpleUnion.b = 1 << 5;
    myGlobalSimpleUnion.c = 1 << 9;

    myGlobalFloatUnion.a = 5.9f;
    myGlobalFloatUnion.b = 1;
    myGlobalFloatUnion.c = 728;
    myGlobalFloatUnion.d = 0.0;

    myGlobalDoubleUnion.a = 9.2f;
    myGlobalDoubleUnion.b = 4.3;
    myGlobalDoubleUnion.c = 19;
    myGlobalDoubleUnion.d = 0.0;

    myGlobalPointerUnion.a = 14;
    myGlobalPointerUnion.b = 23;
    myGlobalPointerUnion.c = 0xabcdef;

    union simpleUnion mySimpleUnion;
    mySimpleUnion.a = 1 << 3;
    mySimpleUnion.b = 1 << 6;
    mySimpleUnion.c = 1 << 8;

    union floatUnion myFloatUnion;
    myFloatUnion.a = 3.7f;
    myFloatUnion.b = 1;
    myFloatUnion.c = 12345;
    myFloatUnion.d = 0.0;

    union doubleUnion myDoubleUnion;
    myDoubleUnion.a = 0.3f;
    myDoubleUnion.b = 7.6;
    myDoubleUnion.c = 5;
    myDoubleUnion.d = 0.0;

    union pointerUnion myPointerUnion;
    myPointerUnion.a = 213;
    myPointerUnion.b = 0x0f0f0f0f;
    myPointerUnion.c = 0xffffffff000000ff;

    return 0;
}
