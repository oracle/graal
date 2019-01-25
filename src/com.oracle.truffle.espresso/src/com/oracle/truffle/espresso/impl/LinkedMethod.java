package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.impl.ParserMethod;

public class LinkedMethod {
    final ParserMethod parserMethod;
    int vtableSlot; // not all methods have vtable entry

    LinkedMethod(ParserMethod parserMethod) {
        this.parserMethod = parserMethod;
    }
}
