package com.oracle.svm.core.sampling;

public interface SamplingMethodData {

    void addEntry(long adr, int methodId);

    int findMethod(long address);

    void addMethodName(long address, String name);

    void dump();

    String findMethodName(long address);
}
