package com.oracle.truffle.espresso.runtime;

public class AttributeInfo {

    private final String name;
    private final byte[] info;

    public String getName() {
        return name;
    }

    public byte[] getRawInfo() {
        return info;
    }

    public AttributeInfo(String name, byte[] info) {
        this.name = name;
        this.info = info;
    }
}
