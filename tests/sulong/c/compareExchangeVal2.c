#include <stdlib.h>
#include <stdio.h>

void testShort0()
{
    short l[2];
    short cmp, repl;
    short *ptr = &(l[0]);
    int replaced;

    l[0] = 32;
    l[1] = 42;

    cmp = 32;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);

    l[0] = 32;
    l[1] = 42;

    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);

}

void testShort1()
{
    short l[2];
    short cmp, repl;
    short *ptr = &(l[1]);
    int replaced;

    l[0] = 32;
    l[1] = 42;

    cmp = 42;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);

    l[0] = 32;
    l[1] = 42;

    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);

}

void testByte0()
{
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[0]);
    int replaced;

    
    cmp = 12;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

void testByte1()
{
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[1]);
    int replaced;

    
    cmp = 22;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

void testByte2()
{
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[2]);
    int replaced;

    
    cmp = 32;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

void testByte3()
{
    char l[4];
    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;

    char cmp, repl;
    char *ptr = &(l[3]);
    int replaced;

    
    cmp = 42;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);

    l[0] = 12;
    l[1] = 22;
    l[2] = 32;
    l[3] = 42;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    printf("%d\n", l[0]);
    printf("%d\n", l[1]);
    printf("%d\n", l[2]);
    printf("%d\n", l[3]);
}

int main()
{
    testShort0();
    testShort1();
    testByte0();
    testByte1();
    testByte2();
    testByte3();
}