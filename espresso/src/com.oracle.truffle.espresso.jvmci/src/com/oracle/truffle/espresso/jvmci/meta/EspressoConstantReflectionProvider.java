/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.jvmci.meta;

import java.lang.reflect.Array;
import java.util.Objects;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoConstantReflectionProvider implements ConstantReflectionProvider {
    private final EspressoMethodHandleAccessProvider methodHandleAccessProvider;
    private final EspressoMetaAccessProvider metaAccess;

    public EspressoConstantReflectionProvider(EspressoMetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        methodHandleAccessProvider = new EspressoMethodHandleAccessProvider();
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return Objects.equals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (array == null || array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        Object a = unwrap((EspressoObjectConstant) array);
        if (!a.getClass().isArray()) {
            return null;
        }
        return Array.getLength(a);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (array == null || array.getJavaKind() != JavaKind.Object || array.isNull()) {
            return null;
        }
        Object a = unwrap((EspressoObjectConstant) array);
        if (!a.getClass().isArray() || index < 0 || index >= Array.getLength(a)) {
            return null;
        }
        if (a instanceof Object[]) {
            Object element = ((Object[]) a)[index];
            return wrap(element);
        } else if (a instanceof int[]) {
            return JavaConstant.forInt(((int[]) a)[index]);
        } else if (a instanceof char[]) {
            return JavaConstant.forChar(((char[]) a)[index]);
        } else if (a instanceof byte[]) {
            return JavaConstant.forByte(((byte[]) a)[index]);
        } else if (a instanceof long[]) {
            return JavaConstant.forLong(((long[]) a)[index]);
        } else if (a instanceof short[]) {
            return JavaConstant.forShort(((short[]) a)[index]);
        } else if (a instanceof float[]) {
            return JavaConstant.forFloat(((float[]) a)[index]);
        } else if (a instanceof double[]) {
            return JavaConstant.forDouble(((double[]) a)[index]);
        } else if (a instanceof boolean[]) {
            return JavaConstant.forBoolean(((boolean[]) a)[index]);
        } else {
            throw new JVMCIError("Should not reach here: " + a.getClass());
        }
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        Objects.requireNonNull(field);
        if (!(field instanceof EspressoResolvedJavaField)) {
            return null;
        }
        EspressoResolvedJavaField espressoField = (EspressoResolvedJavaField) field;
        JavaKind kind = espressoField.getType().getJavaKind();
        if (field.isStatic()) {
            if (!espressoField.getDeclaringClass().isInitialized()) {
                return null;
            }
            switch (kind) {
                case Boolean:
                    return JavaConstant.forBoolean(readStaticBooleanFieldValue(espressoField));
                case Byte:
                    return JavaConstant.forByte(readStaticByteFieldValue(espressoField));
                case Short:
                    return JavaConstant.forShort(readStaticShortFieldValue(espressoField));
                case Char:
                    return JavaConstant.forChar(readStaticCharFieldValue(espressoField));
                case Int:
                    return JavaConstant.forInt(readStaticIntFieldValue(espressoField));
                case Float:
                    return JavaConstant.forFloat(readStaticFloatFieldValue(espressoField));
                case Long:
                    return JavaConstant.forLong(readStaticLongFieldValue(espressoField));
                case Double:
                    return JavaConstant.forDouble(readStaticDoubleFieldValue(espressoField));
                case Object:
                    return wrap(readStaticObjectFieldValue(espressoField));
                default:
                    throw JVMCIError.shouldNotReachHere("Unexpected field kind " + kind);
            }
        } else {
            if (receiver.isNull() || !receiver.getJavaKind().isObject()) {
                return null;
            }
            Class<?> holderClass = ((EspressoResolvedJavaType) espressoField.getDeclaringClass()).getMirror();
            Object objReceiver = unwrap((EspressoObjectConstant) receiver);
            if (!holderClass.isAssignableFrom(objReceiver.getClass())) {
                return null;
            }
            switch (kind) {
                case Boolean:
                    return JavaConstant.forBoolean(readInstanceBooleanFieldValue(espressoField, objReceiver));
                case Byte:
                    return JavaConstant.forByte(readInstanceByteFieldValue(espressoField, objReceiver));
                case Short:
                    return JavaConstant.forShort(readInstanceShortFieldValue(espressoField, objReceiver));
                case Char:
                    return JavaConstant.forChar(readInstanceCharFieldValue(espressoField, objReceiver));
                case Int:
                    return JavaConstant.forInt(readInstanceIntFieldValue(espressoField, objReceiver));
                case Float:
                    return JavaConstant.forFloat(readInstanceFloatFieldValue(espressoField, objReceiver));
                case Long:
                    return JavaConstant.forLong(readInstanceLongFieldValue(espressoField, objReceiver));
                case Double:
                    return JavaConstant.forDouble(readInstanceDoubleFieldValue(espressoField, objReceiver));
                case Object:
                    return wrap(readInstanceObjectFieldValue(espressoField, objReceiver));
                default:
                    throw JVMCIError.shouldNotReachHere("Unexpected field kind " + kind);
            }
        }
    }

    private native boolean readInstanceBooleanFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native byte readInstanceByteFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native short readInstanceShortFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native char readInstanceCharFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native int readInstanceIntFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native float readInstanceFloatFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native long readInstanceLongFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native double readInstanceDoubleFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native Object readInstanceObjectFieldValue(EspressoResolvedJavaField field, Object receiver);

    private native boolean readStaticBooleanFieldValue(EspressoResolvedJavaField field);

    private native byte readStaticByteFieldValue(EspressoResolvedJavaField field);

    private native short readStaticShortFieldValue(EspressoResolvedJavaField field);

    private native char readStaticCharFieldValue(EspressoResolvedJavaField field);

    private native int readStaticIntFieldValue(EspressoResolvedJavaField field);

    private native float readStaticFloatFieldValue(EspressoResolvedJavaField field);

    private native long readStaticLongFieldValue(EspressoResolvedJavaField field);

    private native double readStaticDoubleFieldValue(EspressoResolvedJavaField field);

    private native Object readStaticObjectFieldValue(EspressoResolvedJavaField field);

    /**
     * Check if the constant is a boxed value that is guaranteed to be cached by the platform.
     * Otherwise the generated code might be the only reference to the boxed value and since object
     * references from nmethods are weak this can cause GC problems.
     *
     * @return true if the box is cached
     */
    private static boolean isBoxCached(JavaConstant source) {
        switch (source.getJavaKind()) {
            case Boolean:
                return true;
            case Char:
                return source.asInt() <= 127;
            case Byte:
            case Short:
            case Int:
                return source.asInt() >= -128 && source.asInt() <= 127;
            case Long:
                return source.asLong() >= -128 && source.asLong() <= 127;
            case Float:
            case Double:
                return false;
            default:
                throw new IllegalArgumentException("unexpected kind " + source.getJavaKind());
        }
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        if (source == null || !source.getJavaKind().isPrimitive() || !isBoxCached(source)) {
            return null;
        }
        return wrap(source.asBoxedPrimitive());
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (source == null || source.isNull() || !source.getJavaKind().isObject()) {
            return null;
        }
        return JavaConstant.forBoxedPrimitive(unwrap((EspressoObjectConstant) source));
    }

    @Override
    public JavaConstant forString(String value) {
        return wrap(value);
    }

    private native JavaConstant wrap(Object value);

    private native Object unwrap(EspressoObjectConstant value);

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof KlassConstant) {
            return ((KlassConstant) constant).getType();
        }
        if (constant instanceof EspressoObjectConstant) {
            Object unwrapped = unwrap((EspressoObjectConstant) constant);
            if (unwrapped instanceof Class) {
                return metaAccess.lookupJavaType((Class<?>) unwrapped);
            }
        }
        return null;
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return methodHandleAccessProvider;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return wrap(((EspressoResolvedJavaType) type).getMirror());
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        return new KlassConstant((EspressoResolvedObjectType) type);
    }

    public JavaConstant forObject(Object object) {
        return wrap(object);
    }

    public <T> T asObject(Class<T> type, EspressoObjectConstant constant) {
        Object o = unwrap(constant);
        if (type.isInstance(o)) {
            return type.cast(o);
        }
        return null;
    }

    public native EspressoResolvedInstanceType getTypeForStaticBase(EspressoObjectConstant staticBase);
}
