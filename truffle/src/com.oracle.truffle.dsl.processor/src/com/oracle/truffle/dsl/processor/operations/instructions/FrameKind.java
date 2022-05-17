package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;

public enum FrameKind {
    // order must be the same as FrameSlotKind
    OBJECT("Object", "Object"),
    LONG("long", "Long"),
    INT("int", "Int", "Integer"),
    DOUBLE("double", "Double"),
    FLOAT("float", "Float"),
    BOOLEAN("boolean", "Boolean"),
    BYTE("byte", "Byte");

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

    public TypeMirror getType() {
        ProcessorContext context = ProcessorContext.getInstance();
        switch (this) {
            case BOOLEAN:
                return context.getType(boolean.class);
            case BYTE:
                return context.getType(byte.class);
            case DOUBLE:
                return context.getType(double.class);
            case FLOAT:
                return context.getType(float.class);
            case INT:
                return context.getType(int.class);
            case LONG:
                return context.getType(long.class);
            case OBJECT:
                return context.getType(Object.class);
            default:
                throw new UnsupportedOperationException();

        }
    }
}