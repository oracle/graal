package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.Utf8Constant;

public class AttributeInfo {

    private final Utf8Constant name;
    private final byte[] info;

    public Utf8Constant getName() {
        return name;
    }

    public byte[] getInfo() {
        return info;
    }

    public AttributeInfo(Utf8Constant name, byte[] info) {
        this.name = name;
        this.info = info;
    }
}
