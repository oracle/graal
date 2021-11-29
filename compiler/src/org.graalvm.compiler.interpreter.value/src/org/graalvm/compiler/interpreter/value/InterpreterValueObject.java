package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class InterpreterValueObject extends InterpreterValue {
    final ResolvedJavaType type;
    private boolean unwindException = false;

    public InterpreterValueObject(ResolvedJavaType type) {
        this.type = type;
    }

    public abstract boolean hasField(ResolvedJavaField field);

    public abstract void setFieldValue(ResolvedJavaField field, InterpreterValue value);

    public abstract InterpreterValue getFieldValue(ResolvedJavaField field);

    @Override
    public boolean isUnwindException() {
        return unwindException;
    }

    @Override
    public void setUnwindException() {
        try {
            if (!Exception.class.isAssignableFrom(Class.forName(type.toClassName()))) {
                throw new IllegalArgumentException("cannot unwind with non-Exception object");
            }
        } catch (ClassNotFoundException e) {
            // TODO: does this ever happen for valid graphs?
            throw new IllegalArgumentException();
        }
        this.unwindException = true;
    }

    @Override
    public Object asObject() {
        if (!type.isCloneableWithAllocation()) {
            throw new IllegalArgumentException("Type is not cloneable with just allocation");
        }
        throw new UnsupportedOperationException("not implemented");

        // TODO: use reflection to construct actual java Object: is this doable?
    }

    @Override
    public ResolvedJavaType getObjectType() {
        return type;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isNull() {
        return false;
    }
}
