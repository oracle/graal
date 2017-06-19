#include <stdlib.h>
#include <stdio.h>

void testLong()
{
    long l, cmp, repl;
    long *ptr = &l;;
    long replaced;

    l = 1L;
    cmp = 2L;
    repl = 3L;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
    

    l = 1L;
    cmp = 1L;
    repl = 3L;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
}

void testInt()
{
    int l, cmp, repl;
    int *ptr = &l;;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
}

void testShort()
{
    short l, cmp, repl;
    short *ptr = &l;;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);

}

void testByte()
{
    char l, cmp, repl;
    char *ptr = &l;;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_val_compare_and_swap (ptr, cmp, repl);
    printf("%d\n", replaced);
}


int main()
{
    testLong();
    testInt();
    testShort();
    testByte();
}