package com.oracle.truffle.llvm.tests.interop;

public class CxxVtableTest_TestClass {

    private int last;

    public CxxVtableTest_TestClass() {
        this.last = 0;
    }

    public int foo(int x) {
        int ret = x * 5 + this.last;
        this.last = x;
        return ret;
    }
}
