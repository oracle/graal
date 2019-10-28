package com.oracle.truffle.espresso.jdwp.api;

public interface JDWPVirtualMachine {

    int getSizeOfFieldRef();

    int getSizeOfMethodRef();

    int getSizeofObjectRefRef();

    int getSizeOfClassRef();

    int getSizeOfFrameRef();

    String getVmDescription();

    String getVmVersion();

    String getVmName();
}
