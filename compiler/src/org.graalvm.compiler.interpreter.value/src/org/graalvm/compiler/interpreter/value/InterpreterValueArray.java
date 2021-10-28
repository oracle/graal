package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InterpreterValueArray extends InterpreterValue {
    private final ResolvedJavaType componentType;
    private final int length;
    private final InterpreterValue[] contents;

    public InterpreterValueArray(ResolvedJavaType componentType, int length, JavaKind storageKind) {
        this(componentType, length, storageKind, true);
    }

    public InterpreterValueArray(ResolvedJavaType componentType, int length, JavaKind storageKind, boolean populateDefault) {
        if (length < 0) {
            throw new IllegalArgumentException("Negative array length");
        }

        this.componentType = componentType;
        this.length = length;
        this.contents = new InterpreterValue[length];
        if (populateDefault) {
            populateContentsWithDefaultValues(storageKind);
        }
    }

    private void populateContentsWithDefaultValues(JavaKind storageKind) {
        for (int i = 0; i < length; i++) {
            contents[i] = InterpreterValue.createDefaultOfKind(storageKind);
        }
    }

    public int getLength() {
        return length;
    }

    @Override
    public ResolvedJavaType getObjectType() {
        return componentType.getArrayClass();
    }

    public ResolvedJavaType getComponentType() {
        return componentType;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public Object asObject() {
        // TODO: figure out how to do this
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isArray() {
        return true;
    }

    public InterpreterValue getAtIndex(int index) {
        checkBounds(index);
        if (contents[index] == null) {
            throw new IllegalStateException();
        }
        return contents[index];
    }

    public void setAtIndex(int index, InterpreterValue value) {
        checkBounds(index);
        // TODO: should we bother checking type compatbilitity?
        contents[index] = value;
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= length) {
            throw new IllegalArgumentException("Invalid array access index");
        }
    }
}
