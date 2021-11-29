package org.graalvm.compiler.interpreter.value;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.function.Function;

/**
 * Portrays a read-only view of a real Java object.
 *
 * Fields can be read, but not written.
 */
public class InterpreterValueConstantObject extends InterpreterValueObject {
    final Object realObject;

    public InterpreterValueConstantObject(ResolvedJavaType type, Object value) {
        super(type);
        this.realObject = value;
    }

    @Override
    public boolean hasField(ResolvedJavaField field) {
        try {
            // TODO: try superclass fields too
            Field f = realObject.getClass().getDeclaredField(field.getName());
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    @Override
    public void setFieldValue(ResolvedJavaField field, InterpreterValue value) {
        throw new IllegalArgumentException("cannot set field of constant object: " + realObject);
    }

    @Override
    public InterpreterValue getFieldValue(ResolvedJavaField field) {
        try {
            Field f = realObject.getClass().getDeclaredField(field.getName());
            Object obj = f.get(this.realObject);
            if (obj instanceof Integer) {
                return InterpreterValuePrimitive.ofInt(((Integer) obj).intValue());
            } else if (obj instanceof Boolean) {
                return InterpreterValuePrimitive.ofBoolean(((Boolean) obj).booleanValue());
            } else {
                return new InterpreterValueConstantObject(this.type, obj);
            }
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException(ex);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

}
