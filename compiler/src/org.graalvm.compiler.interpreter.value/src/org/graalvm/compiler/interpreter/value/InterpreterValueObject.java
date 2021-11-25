package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.HashMap;
import java.util.function.Function;

public class InterpreterValueObject extends InterpreterValue {
    final ResolvedJavaType type;
    final HashMap<ResolvedJavaField, InterpreterValue> instanceFields;
    private boolean unwindException = false;

    public InterpreterValueObject(ResolvedJavaType type, Function<JavaType, JavaKind> getStorageKind) {
        this.type = type;
        this.instanceFields = new HashMap<>();

        for (ResolvedJavaField field : type.getInstanceFields(true)) {
            this.instanceFields.put(field, InterpreterValue.createDefaultOfKind(getStorageKind.apply(field.getType())));
        }
    }

    public boolean hasField(ResolvedJavaField field) {
        return instanceFields.containsKey(field);
    }

    public void setFieldValue(ResolvedJavaField field, InterpreterValue value) {
        instanceFields.replace(field, value);
    }

    public InterpreterValue getFieldValue(ResolvedJavaField field) {
        return instanceFields.get(field);
    }

    @Override
    public boolean isUnwindException() {
        return unwindException;
    }

    @Override
    public void setUnwindException() {
        try {
            if (! Exception.class.isAssignableFrom(Class.forName(type.toClassName()))) {
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
