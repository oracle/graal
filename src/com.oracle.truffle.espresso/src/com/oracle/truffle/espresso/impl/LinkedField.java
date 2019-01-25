package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.espresso.impl.ParserField;

public class LinkedField {
    private final ParserField parserField;

    int slot;
    Location location;

    public LinkedField(ParserField parserField) {
        this.parserField = parserField;
    }
}
