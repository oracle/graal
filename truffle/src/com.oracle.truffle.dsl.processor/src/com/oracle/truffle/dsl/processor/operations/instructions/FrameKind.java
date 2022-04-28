package com.oracle.truffle.dsl.processor.operations.instructions;

public enum FrameKind {
    BOOLEAN("boolean", "Boolean"),
    BYTE("byte", "Byte"),
    INT("int", "Int", "Integer"),
    FLOAT("float", "Float"),
    LONG("long", "Long"),
    DOUBLE("double", "Double"),
    OBJECT("Object", "Object");

    private final String typeName;
    private final String frameName;
    private final String typeNameBoxed;

    private FrameKind(String typeName, String frameName) {
        this(typeName, frameName, frameName);
    }

    private FrameKind(String typeName, String frameName, String typeNameBoxed) {
        this.typeName = typeName;
        this.frameName = frameName;
        this.typeNameBoxed = typeNameBoxed;
    }

    public boolean isSingleByte() {
        return this == BOOLEAN || this == BYTE;
    }

    public boolean isBoxed() {
        return this == OBJECT;
    }

    public String getFrameName() {
        return frameName;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getTypeNameBoxed() {
        return typeNameBoxed;
    }
}