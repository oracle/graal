package com.oracle.truffle.espresso.debugger.api;

public interface LocalRef {
    int getStartBCI();

    String getNameAsString();

    String getTypeAsString();

    int getEndBCI();

    int getSlot();
}
