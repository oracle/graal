/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.webimage.object;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicSet;

import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class ObjectInspector {

    public static final ObjectType NULL = new ObjectType(JavaConstant.NULL_POINTER, null, null, null);
    public static final ValueType TRUE = ValueType.forConstant(JavaConstant.TRUE);
    public static final ValueType FALSE = ValueType.forConstant(JavaConstant.FALSE);
    public static final ValueType ZERO_BYTE = ValueType.forConstant(JavaConstant.forByte((byte) 0));
    public static final ValueType ZERO_SHORT = ValueType.forConstant(JavaConstant.forShort((short) 0));
    public static final ValueType ZERO_CHAR = ValueType.forConstant(JavaConstant.forChar((char) 0));
    public static final ValueType ZERO_INT = ValueType.forConstant(JavaConstant.INT_0);
    public static final ValueType ZERO_LONG = ValueType.forConstant(JavaConstant.LONG_0);
    public static final ValueType ZERO_FLOAT = ValueType.forConstant(JavaConstant.FLOAT_0);
    public static final ValueType ZERO_DOUBLE = ValueType.forConstant(JavaConstant.DOUBLE_0);

    protected boolean isFrozen = false;

    public void freeze() {
        assert !isFrozen : "Object inspector is already frozen";
        isFrozen = true;
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    /**
     * Inspect an object and recursively all objects it references.
     *
     * Produces a unique {@link ObjectDefinition} for the object. If an object is inspected multiple
     * times, the same {@link ObjectDefinition} is returned every time.
     *
     * @param reason Reason why the object is being inspected.
     * @param identityMapping Cache for inspected objects.
     * @return The {@link ObjectDefinition} for the object.
     */
    public abstract ObjectDefinition inspectObject(JavaConstant c, Object reason, ConstantIdentityMapping identityMapping);

    /**
     * Stores all emitted fields for a specific type.
     */
    public static class ClassFieldList {
        public List<HostedField> fields;
        public HostedType type;

        private static final AtomicInteger ID = new AtomicInteger(0);

        /**
         * Unique ID for each field list.
         * <p>
         * We use a separate ID field instead of the type id because the type IDs are generally
         * sparse if not all types are included in the image.
         */
        private final int id = ID.getAndIncrement();

        public static int getNumIDs() {
            return ID.get();
        }

        public int getId() {
            return id;
        }
    }

    public abstract static class ObjectDefinition {
        public int offset = -1;

        private final EconomicSet<Object> reasons = EconomicSet.create();

        private final JavaConstant constant;

        protected ObjectDefinition(JavaConstant constant, Object reason) {
            this.constant = constant;
            addReason(reason);
        }

        public final void addReason(Object r) {
            if (r != null) {
                reasons.add(r);
            }
        }

        public Object[] getReasons() {
            return reasons.toArray(new Object[reasons.size()]);
        }

        public JavaConstant getConstant() {
            return constant;
        }

        /**
         * Approximate size used by the object, only use this for analysing code size.
         * <p>
         * Does not include the size of referenced objects, such references have a fixed size. The
         * size of individual components should be determined by {@link #getKindSize(JavaKind)}
         */
        public abstract long getSize();

        public static long getKindSize(JavaKind kind) {
            return kind.isObject() ? 8 : kind.getByteCount();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ObjectDefinition other) {
                return Objects.equals(getConstant(), other.getConstant());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return getConstant().hashCode();
        }
    }

    public static final class ValueType extends ObjectDefinition {

        public final JavaKind kind;

        private ValueType(PrimitiveConstant constant) {
            super(constant, null);
            this.kind = constant.getJavaKind();
        }

        public static ValueType forConstant(PrimitiveConstant c) {
            ValueType t = new ValueType(c);
            t.guaranteeHasType();
            return t;
        }

        @Override
        public PrimitiveConstant getConstant() {
            return (PrimitiveConstant) super.getConstant();
        }

        @Override
        public long getSize() {
            return getKindSize(kind);
        }

        public boolean asBoolean() {
            assert kind == JavaKind.Boolean : kind;
            return getConstant().asBoolean();
        }

        public byte asByte() {
            assert kind == JavaKind.Byte : kind;
            return (byte) getConstant().asInt();
        }

        public short asShort() {
            assert kind == JavaKind.Short : kind;
            return (short) getConstant().asInt();
        }

        public char asChar() {
            assert kind == JavaKind.Char : kind;
            return (char) getConstant().asInt();
        }

        public int asInt() {
            assert kind == JavaKind.Int : kind;
            return getConstant().asInt();
        }

        public long asLong() {
            assert kind == JavaKind.Long : kind;
            return getConstant().asLong();
        }

        public float asFloat() {
            assert kind == JavaKind.Float : kind;
            return getConstant().asFloat();
        }

        public double asDouble() {
            assert kind == JavaKind.Double : kind;
            return getConstant().asDouble();
        }

        public Object getBoxed() {
            return getConstant().asBoxedPrimitive();
        }

        public static String floatWithSpecialCases(float f) {
            if (Float.isNaN(f)) {
                return "Number.NaN";
            } else if (f == Float.NEGATIVE_INFINITY) {
                return "Number.NEGATIVE_INFINITY";
            } else if (f == Float.POSITIVE_INFINITY) {
                return "Number.POSITIVE_INFINITY";
            } else {
                return String.valueOf(f);
            }
        }

        public static String doubleWithSpecialCases(double d) {
            if (Double.isNaN(d)) {
                return "Number.NaN";
            } else if (d == Double.NEGATIVE_INFINITY) {
                return "Number.NEGATIVE_INFINITY";
            } else if (d == Double.POSITIVE_INFINITY) {
                return "Number.POSITIVE_INFINITY";
            } else {
                return String.valueOf(d);
            }
        }

        public void guaranteeHasType() {
            JVMCIError.guarantee(kind.isPrimitive() && kind != JavaKind.Void, "Primitive value cannot be without any type");
        }

        @Override
        public String toString() {
            return "ValueType(" + getConstant().toString() + ")";
        }
    }

    public static class ObjectType extends ObjectDefinition {
        public List<ObjectDefinition> members;
        public final HostedType type;
        public final ClassFieldList fields;

        public ObjectType(JavaConstant constant, HostedType type, ClassFieldList fields, Object reason) {
            super(constant, reason);
            this.type = type;
            this.fields = fields;
        }

        public boolean isNull() {
            return getConstant().isNull();
        }

        @Override
        public long getSize() {
            return fields.fields.stream().mapToLong(f -> getKindSize(f.getJavaKind())).sum();
        }

        @Override
        public String toString() {
            return "ObjectType(" + type.toClassName() + ", " + getConstant() + ")";
        }
    }

    public static class ArrayType<T extends ObjectDefinition> extends ObjectDefinition {
        /**
         * Stores one ObjectDefinition for every array element.
         */
        public List<T> elements;
        public final HostedType componentType;
        /**
         * Whether all array elements are equal.
         * <p>
         * Is false if the array is empty
         */
        public boolean isAllEqual = false;

        public ArrayType(JavaConstant constant, HostedType componentType, Object reason) {
            super(constant, reason);
            this.componentType = componentType;
        }

        public int length() {
            return elements.size();
        }

        @Override
        public long getSize() {
            return length() * getKindSize(componentType.getJavaKind());
        }

        public boolean isPrimitive() {
            return componentType.isPrimitive();
        }

        @Override
        public String toString() {
            return "ArrayType(" + componentType.toClassName() + ", " + elements.size() + ")";
        }

    }

    public static class StringType extends ObjectDefinition {
        public final String stringVal;

        protected byte[] bytes = null;

        public StringType(JavaConstant constant, String stringVal, Object reason) {
            super(constant, reason);
            this.stringVal = stringVal;
        }

        @Override
        public long getSize() {
            return getBytes().length;
        }

        /**
         * Returns the bytes of the UTF-8 encoded string.
         */
        public byte[] getBytes() {
            if (bytes == null) {
                bytes = stringVal.getBytes(StandardCharsets.UTF_8);
            }

            return bytes;
        }

        @Override
        public String toString() {
            return "StringType(" + stringVal + ")";
        }
    }

    /**
     * Method pointers are used to implement reflection support in SVM.
     * <p>
     * We lower a method pointer {@code C.foo} to {@code C.prototype.foo}.
     */
    public static class MethodPointerType extends ObjectDefinition {
        private final ResolvedJavaMethod method;
        private final long index;

        public MethodPointerType(JavaConstant constant, ResolvedJavaMethod method, int index, Object reason) {
            super(constant, reason);
            Objects.requireNonNull(method);
            this.method = method;
            /*
             * runtime.funtab starts from 1 as it uses null as the first element.
             *
             * See runtime.js
             */
            this.index = index + 1;
        }

        public ResolvedJavaMethod getMethod() {
            return method;
        }

        public long getIndex() {
            return index;
        }

        @Override
        public long getSize() {
            return getKindSize(JavaKind.Long);
        }

        @Override
        public String toString() {
            return "MethodPointerType(" + index + "," + method.getName() + ")";
        }
    }

}
