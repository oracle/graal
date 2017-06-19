#include <stdlib.h>
#include <stdio.h>

void testLong()
{
    long l, cmp, repl;
    long *ptr = &l;;
    int replaced;

    l = 1L;
    cmp = 2L;
    repl = 3L;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1L;
    cmp = 1L;
    repl = 3L;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testInt()
{
    int l, cmp, repl;
    int *ptr = &l;;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testShort()
{
    short l, cmp, repl;
    short *ptr = &l;;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testByte()
{
    char l, cmp, repl;
    char *ptr = &l;;
    int replaced;

    l = 1;
    cmp = 2;
    repl = 3;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = 1;
    cmp = 1;
    repl = 3;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

void testPointer()
{
    // the llvm cmpxchg instruction supports pointers, but llvm maps this to an i64 comparison
    char origL = 1, origCmp = 2, origRepl = 3;
    char *l;
    char *cmp;
    char *repl;
    char **ptr = &l;
    int replaced;

    l = &origL;
    cmp = &origCmp;
    repl = &origRepl;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (replaced || l == repl) {
        abort();
    }

    l = &origL;
    cmp = &origL;
    repl = &origRepl;
    replaced = __sync_bool_compare_and_swap (ptr, cmp, repl);
    if (!replaced || l != repl) {
        abort();
    }
}

int main()
{
    testLong();
    testInt();
    testShort();
    testByte();
    testPointer();
}