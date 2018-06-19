package com.oracle.truffle.espresso.classfile;

public class NameAndType {

    private final String name;
    private final String descriptor;

    public NameAndType(String name, String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }
}
