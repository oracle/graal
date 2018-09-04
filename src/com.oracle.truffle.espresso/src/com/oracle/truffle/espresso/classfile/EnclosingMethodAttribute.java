package com.oracle.truffle.espresso.classfile;

import com.oracle.truffle.espresso.runtime.AttributeInfo;

public class EnclosingMethodAttribute extends AttributeInfo {
    private final int classIndex;
    private final int methodIndex;
    public EnclosingMethodAttribute(String name, int classIndex, int methodIndex) {
        super(name, null);
        this.classIndex = classIndex;
        this.methodIndex = methodIndex;
    }

    public int getMethodIndex() {
        return methodIndex;
    }

    public int getClassIndex() {
        return classIndex;
    }
}
