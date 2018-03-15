typedef unsigned int myUnsignedInt;

int start() __attribute__((constructor))
{
    myUnsignedInt typedefedVal = 15;
    const int constVal = 234;
    const myUnsignedInt cuVal = 128;
    volatile int volatileVal = 756;
    return 0;
}
