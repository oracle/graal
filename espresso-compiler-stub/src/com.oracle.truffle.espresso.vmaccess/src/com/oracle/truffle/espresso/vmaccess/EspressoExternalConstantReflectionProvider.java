/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import java.util.Objects;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.ConstantReflectionProviderWithStaticsBase;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType;
import com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedObjectType;
import com.oracle.truffle.espresso.jvmci.meta.KlassConstant;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

final class EspressoExternalConstantReflectionProvider implements ConstantReflectionProviderWithStaticsBase {
    private final EspressoExternalVMAccess access;
    private final EspressoExternalMethodHandleAccessProvider methodHandleProvider;

    EspressoExternalConstantReflectionProvider(EspressoExternalVMAccess access) {
        this.access = access;
        this.methodHandleProvider = new EspressoExternalMethodHandleAccessProvider(access);
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        return Objects.equals(x, y);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        if (!(array instanceof EspressoExternalObjectConstant objectConstant)) {
            return null;
        }
        try {
            return (int) objectConstant.getValue().getArraySize();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        if (!(array instanceof EspressoExternalObjectConstant objectConstant)) {
            return null;
        }
        EspressoResolvedObjectType objectType = objectConstant.getType();
        if (!objectType.isArray()) {
            return null;
        }
        JavaKind componentKind = objectType.getComponentType().getJavaKind();
        Value v;
        try {
            Value arrayAsValue = objectConstant.getValue();
            v = componentKind.isPrimitive() ? arrayAsValue.getArrayElement(index) : access.invokeJVMCIHelper("readObjectArrayElement", arrayAsValue, index);
        } catch (PolyglotException e) {
            try {
                throw EspressoExternalVMAccess.throwHostException(e);
            } catch (IndexOutOfBoundsException ignored) {
                return null;
            }
        }
        return asJavaConstant(v, componentKind, access);
    }

    static Class<?> safeGetClass(Object o) {
        if (o == null) {
            return null;
        }
        return o.getClass();
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        if (receiver != null && !(receiver instanceof EspressoExternalObjectConstant)) {
            throw new IllegalArgumentException("expected an espresso receiver, got a " + receiver.getClass());
        }
        EspressoExternalObjectConstant espressoReceiver = (EspressoExternalObjectConstant) receiver;
        if (!(field instanceof EspressoExternalResolvedJavaField espressoField)) {
            throw new IllegalArgumentException("expected an espresso field, got a " + safeGetClass(field));
        }
        Value receiverValue;
        if (espressoField.isStatic()) {
            EspressoExternalResolvedInstanceType declaringClass = (EspressoExternalResolvedInstanceType) espressoField.getDeclaringClass();
            if (!declaringClass.isInitialized()) {
                return null;
            }
            receiverValue = declaringClass.getMetaObject();
        } else {
            if (receiver == null || !espressoField.getDeclaringClass().isAssignableFrom(espressoReceiver.getType())) {
                return null;
            }
            receiverValue = espressoReceiver.getValue();
        }
        Value value = espressoField.readValue(receiverValue);
        return asJavaConstant(value, espressoField.getJavaKind(), access);
    }

    static JavaConstant asJavaConstant(Value value, JavaKind kind, EspressoExternalVMAccess access) {
        return switch (kind) {
            case Boolean -> JavaConstant.forBoolean(value.asBoolean());
            case Byte -> JavaConstant.forByte(value.asByte());
            case Short -> JavaConstant.forShort(value.asShort());
            case Char -> JavaConstant.forChar(value.as(Character.class));
            case Int -> JavaConstant.forInt(value.asInt());
            case Long -> JavaConstant.forLong(value.asLong());
            case Float -> JavaConstant.forFloat(value.asFloat());
            case Double -> JavaConstant.forDouble(value.asDouble());
            case Object -> asObjectConstant(value, access);
            default -> throw JVMCIError.shouldNotReachHere(kind.toString());
        };
    }

    static JavaConstant asObjectConstant(Value value, EspressoExternalVMAccess access) {
        if (value.isNull()) {
            return JavaConstant.NULL_POINTER;
        }
        return new EspressoExternalObjectConstant(access, value);
    }

    @Override
    public JavaConstant boxPrimitive(JavaConstant source) {
        JavaKind kind = source.getJavaKind();
        if (!kind.isPrimitive()) {
            return null;
        }
        EspressoExternalResolvedJavaMethod method = switch (kind) {
            case Boolean -> access.java_lang_Boolean_valueOf;
            case Byte -> access.java_lang_Byte_valueOf;
            case Short -> access.java_lang_Short_valueOf;
            case Char -> access.java_lang_Character_valueOf;
            case Int -> access.java_lang_Integer_valueOf;
            case Long -> access.java_lang_Long_valueOf;
            case Float -> access.java_lang_Float_valueOf;
            case Double -> access.java_lang_Double_valueOf;
            default -> throw JVMCIError.shouldNotReachHere(kind.toString());
        };
        return access.invoke(method, null, source);
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        if (!(source instanceof EspressoExternalObjectConstant objectConstant)) {
            return null;
        }
        EspressoResolvedObjectType sourceType = objectConstant.getType();
        if (!(sourceType instanceof EspressoExternalResolvedInstanceType sourceInstanceType)) {
            return null;
        }
        if (sourceInstanceType.equals(access.java_lang_Boolean)) {
            return access.java_lang_Boolean_booleanValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Byte)) {
            return access.java_lang_Byte_byteValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Short)) {
            return access.java_lang_Short_shortValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Character)) {
            return access.java_lang_Character_charValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Integer)) {
            return access.java_lang_Integer_intValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Long)) {
            return access.java_lang_Long_longValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Float)) {
            return access.java_lang_Float_floatValue.invoke(source);
        } else if (sourceInstanceType.equals(access.java_lang_Double)) {
            return access.java_lang_Double_doubleValue.invoke(source);
        }
        throw JVMCIError.shouldNotReachHere(sourceInstanceType.toString());
    }

    @Override
    public JavaConstant forString(String value) {
        Value guestString = access.invokeJVMCIHelper("toGuestString", value);
        return new EspressoExternalObjectConstant(access, guestString);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof EspressoExternalObjectConstant espressoConstant) {
            // j.l.Class?
            Value value = espressoConstant.getValue();
            if ("java.lang.Class".equals(value.getMetaObject().getMetaQualifiedName())) {
                return classAsType(value, access);
            }
            return null;
        }
        if (constant instanceof KlassConstant klassConstant) {
            return klassConstant.getType();
        }
        return null;
    }

    static EspressoResolvedJavaType classAsType(Value value, EspressoExternalVMAccess access) {
        if (value.invokeMember("isArray").asBoolean()) {
            Value elemental = value;
            int dimensions = 0;
            do {
                dimensions++;
                elemental = elemental.invokeMember("getComponentType");
            } while (elemental.invokeMember("isArray").asBoolean());
            return new EspressoExternalResolvedArrayType(getNonArrayType(elemental, access), dimensions, access);
        }
        return getNonArrayType(value, access);
    }

    static EspressoExternalResolvedJavaMethod methodAsJavaResolvedMethod(Value reflectExecutable, EspressoExternalVMAccess access) {
        Value declaringClass = reflectExecutable.invokeMember("getDeclaringClass");
        EspressoExternalResolvedInstanceType holder = (EspressoExternalResolvedInstanceType) getNonArrayType(declaringClass, access);
        Value vmMethod = access.invokeJVMCIHelper("getVMMethod", reflectExecutable);
        return new EspressoExternalResolvedJavaMethod(holder, vmMethod, reflectExecutable);
    }

    static EspressoExternalResolvedJavaField fieldAsJavaResolvedField(Value reflectField, EspressoExternalVMAccess access) {
        Value declaringClass = reflectField.invokeMember("getDeclaringClass");
        EspressoExternalResolvedInstanceType holder = (EspressoExternalResolvedInstanceType) getNonArrayType(declaringClass, access);
        Value vmField = access.invokeJVMCIHelper("getVMField", reflectField);
        return new EspressoExternalResolvedJavaField(holder, vmField, reflectField);
    }

    private static EspressoResolvedJavaType getNonArrayType(Value value, EspressoExternalVMAccess access) {
        if (value.invokeMember("isPrimitive").asBoolean()) {
            return getPrimitiveType(value.getMember("static").getMetaQualifiedName(), access);
        }
        assert !value.invokeMember("isArray").asBoolean();
        return new EspressoExternalResolvedInstanceType(access, value.getMember("static"));
    }

    private static EspressoExternalResolvedPrimitiveType getPrimitiveType(String name, EspressoExternalVMAccess access) {
        JavaKind kind = switch (name) {
            case "boolean" -> JavaKind.Boolean;
            case "byte" -> JavaKind.Byte;
            case "short" -> JavaKind.Short;
            case "char" -> JavaKind.Char;
            case "int" -> JavaKind.Int;
            case "long" -> JavaKind.Long;
            case "float" -> JavaKind.Float;
            case "double" -> JavaKind.Double;
            case "void" -> JavaKind.Void;
            default -> throw JVMCIError.shouldNotReachHere(name);
        };
        assert kind.getJavaName().equals(name);
        return access.forPrimitiveKind(kind);
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        return methodHandleProvider;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public EspressoExternalObjectConstant asJavaClass(ResolvedJavaType type) {
        switch (type) {
            case EspressoExternalResolvedInstanceType espressoType -> {
                return new EspressoExternalObjectConstant(access, espressoType.getMetaObject().getMember("class"));
            }
            case EspressoExternalResolvedPrimitiveType primitiveType -> {
                return new EspressoExternalObjectConstant(access, access.getPrimitiveClass(primitiveType.getJavaKind()));
            }
            case EspressoExternalResolvedArrayType arrayType -> {
                EspressoExternalObjectConstant result = asJavaClass(arrayType.getElementalType());
                int dimensions = arrayType.getDimensions();
                for (int i = 0; i < dimensions; i++) {
                    result = (EspressoExternalObjectConstant) access.invoke(access.java_lang_Class_arrayType, result);
                }
                return result;
            }
            default -> throw new IllegalArgumentException("expected an espresso type, got a " + type.getClass());
        }
    }

    @Override
    public Constant asObjectHub(ResolvedJavaType type) {
        if (!(type instanceof EspressoResolvedObjectType espressoType)) {
            throw new IllegalArgumentException("expected an espresso object type, got a " + type.getClass());
        }
        return new KlassConstant(espressoType);
    }

    @Override
    public int identityHashCode(JavaConstant constant) {
        JavaKind kind = Objects.requireNonNull(constant).getJavaKind();
        if (kind != JavaKind.Object) {
            throw new IllegalArgumentException("Constant has unexpected kind " + kind + ": " + constant);
        }
        if (constant.isNull()) {
            /* System.identityHashCode is specified to return 0 when passed null. */
            return 0;
        }
        if (!(constant instanceof EspressoExternalObjectConstant objectConstant)) {
            throw new IllegalArgumentException("Constant has unexpected type " + constant.getClass() + ": " + constant);
        }
        return objectConstant.guestHashCode();
    }

    @Override
    public int makeIdentityHashCode(JavaConstant constant, int requestedValue) {
        JavaKind kind = Objects.requireNonNull(constant).getJavaKind();
        if (kind != JavaKind.Object) {
            throw new IllegalArgumentException("Constant has unexpected kind " + kind + ": " + constant);
        }
        if (constant.isNull()) {
            throw new NullPointerException();
        }
        if (!(constant instanceof EspressoExternalObjectConstant objectConstant)) {
            throw new IllegalArgumentException("Constant has unexpected type " + constant.getClass() + ": " + constant);
        }
        if (requestedValue <= 0) {
            throw new IllegalArgumentException("hashcode must be > 0");
        }
        return access.invokeJVMCIHelper("makeIdentityHashCode", objectConstant.getValue(), requestedValue).asInt();
    }

    @Override
    public AbstractEspressoResolvedInstanceType getTypeForStaticBase(JavaConstant staticBase) {
        if (!(staticBase instanceof EspressoExternalObjectConstant objectConstant)) {
            return null;
        }
        Value type = access.invokeJVMCIHelper("getTypeForStaticBase", objectConstant.getValue());
        if (type.isNull()) {
            return null;
        }
        return new EspressoExternalResolvedInstanceType(access, type);
    }
}
