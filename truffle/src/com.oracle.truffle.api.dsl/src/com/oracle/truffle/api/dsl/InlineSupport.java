/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.DSLSupport.SpecializationDataNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * Contains classes to support node object inlining in Truffle. These classes are only needed if
 * manual node inlining is implemented. Typically Truffle DSL's {@link GenerateInline} takes care of
 * applying these APIs correctly. For manual usage see
 * {@link com.oracle.truffle.api.profiles.InlinedBranchProfile} as an example.
 *
 * @see GenerateInline
 * @since 23.0
 */
public final class InlineSupport {

    private InlineSupport() {
        // no instances
    }

    /**
     * Shortcut to {@link InlinableField#validate(Node) validate} multiple inlinable fields.
     *
     * @since 23.0
     **/
    public static boolean validate(Node node, InlinableField field0, InlinableField field1, InlinableField... fields) {
        field0.validate(node);
        field1.validate(node);
        for (InlinableField field : fields) {
            field.validate(node);
        }
        return true;
    }

    /**
     * Shortcut to {@link InlinableField#validate(Node) validate} multiple inlinable fields.
     *
     * @since 23.0
     **/
    public static boolean validate(Node node, InlinableField field0, InlinableField field1) {
        field0.validate(node);
        field1.validate(node);
        return true;
    }

    /**
     * Shortcut to {@link InlinableField#validate(Node) validate} multiple inlinable fields.
     *
     * @since 23.0
     **/
    public static boolean validate(Node node, InlinableField field0) {
        field0.validate(node);
        return true;
    }

    private static String getEnclosingSimpleName(Class<?> c) {
        if (c.getEnclosingClass() != null) {
            return getEnclosingSimpleName(c.getEnclosingClass()) + "." + c.getSimpleName();
        }
        return c.getSimpleName();
    }

    static final ReferenceField<Node> PARENT = new ReferenceField<>(Node.class, Node.class, DSLAccessor.nodeAccessor().nodeLookup(), "parent", Node.class);

    /**
     * Used to specify fields for node object inlining in inline methods for the {@link InlineTarget
     * inline target}.
     * <p>
     * See {@link InlineTarget} for a full usage example.
     *
     * @since 23.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER})
    @Repeatable(RequiredFields.class)
    public @interface RequiredField {

        /**
         * Species which field type is expected. See subclasses of {@link InlinableField}.
         *
         * @since 23.0
         */
        Class<? extends InlinableField> value();

        /**
         * Specifies the number of bits needed for {@link StateField state fields}. This property
         * only has an effect for {@link StateField}. The number of bits must be between
         * <code>1</code> and<code>32</code>.
         *
         * @since 23.0
         */
        int bits() default 0;

        /**
         * 90 Specifies the the value type for {@link ReferenceField reference} required fields.
         * This property only has an effect for {@link ReferenceField}.
         *
         * @since 23.0
         */
        Class<?> type() default InlinableField.class;

        /**
         * Specifies the compilation final {@link CompilationFinal#dimensions() dimensions} of the
         * required inlined field. This property has only an effect with array types and
         * {@link ReferenceField reference fields}.
         *
         * @since 23.0
         */
        int dimensions() default 0;
    }

    /**
     * Marks a field to be accessed with unsafe. This annotation is useful to communicate fields
     * that must not e rewritten by code obfuscation tools like Proguard.
     *
     * @since 23.0
     */
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.FIELD})
    public @interface UnsafeAccessedField {
    }

    /**
     * Used to specify multiple {@link RequiredField}.
     *
     * @see RequiredField
     * @since 23.0
     **/
    @Retention(RetentionPolicy.CLASS)
    @Target({ElementType.PARAMETER})
    public @interface RequiredFields {

        /**
         * Used to specify multiple {@link RequiredField}.
         *
         * @since 23.0
         **/
        RequiredField[] value();

    }

    /**
     * An inline targert for an inlinable node. This is used as first parameter of an inline method.
     * The inline method is used by generated Truffle DSL code as well as manually written inlinable
     * nodes.
     *
     * Usage example:
     *
     * <pre>
     * public static InlinedCountingConditionProfile inline(
     *                 &#64;RequiredField(value = StateField.class, bits = 7) //
     *                 &#64;RequiredField(value = PrimitiveIntField.class) //
     *                 &#64;RequiredField(value = ReferenceField.class, type = String.class) InlineTarget target) {
     *     StateField state = target.getState(0, 7);
     *     PrimitiveIntField primitive = target.getPrimitive(1, PrimitiveIntField.class);
     *     ReferenceField reference = target.getReference(2, String.class);
     *
     *     // pass fields on to inline node
     * }
     * </pre>
     *
     * @since 23.0
     */
    public static final class InlineTarget {

        private final Class<?> targetClass;
        private final InlinableField[] updaters;

        InlineTarget(Class<?> targetClass, InlinableField[] updaters) {
            this.targetClass = targetClass;
            this.updaters = updaters;
        }

        /**
         * Returns static target class this inlining specification was applied.
         *
         * @since 23.0
         */
        public Class<?> getTargetClass() {
            return targetClass;
        }

        /**
         * Requests a primitive field for a given field index. Fields that are requested from a
         * target must match the required fields specified using {@link RequiredField} on the target
         * parameter of an inline method otherwise an {@link IncompatibleClassChangeError} is
         * thrown.
         *
         * @since 23.0
         */
        public <T extends InlinableField> T getPrimitive(int index, Class<T> fieldClass) {
            Objects.requireNonNull(fieldClass);
            if (!isPrimitiveField(fieldClass)) {
                throw incompatibleAccessError(String.format("Invalid or modified field type. Expected primitive field but got %s.", fieldClass.getName()));
            }
            return get(index, fieldClass);
        }

        /**
         * Requests a state field for a given field index. Fields that are requested from a target
         * must match the required fields specified using {@link RequiredField} on the target
         * parameter of an inline method otherwise an {@link IncompatibleClassChangeError} is
         * thrown.
         *
         * @since 23.0
         */
        public StateField getState(int index, int minimumBits) {
            if (minimumBits <= 0 || minimumBits > 32) {
                throw new IllegalArgumentException("Invalid minimum bits. Expected >= 0 and <= 32 but was " + minimumBits + ".");
            }
            StateField field = get(index, StateField.class);
            if (!(minimumBits <= field.bitLength)) {
                throw incompatibleAccessError(
                                String.format("Expected minimum state bits %s, but got %s.",
                                                minimumBits, field.bitLength));
            }
            return field;
        }

        /**
         * Requests a reference field for a given field index. Fields that are requested from a
         * target must match the required fields specified using {@link RequiredField} on the target
         * parameter of an inline method otherwise an {@link IncompatibleClassChangeError} is
         * thrown.
         *
         * @since 23.0
         */
        @SuppressWarnings("unchecked")
        public <V> ReferenceField<V> getReference(int index, Class<?> valueClass) {
            Objects.requireNonNull(valueClass);
            ReferenceField<?> reference = get(index, ReferenceField.class);
            Class<?> varType = reference.valueClass;
            if (!varType.isAssignableFrom(valueClass)) {
                throw incompatibleAccessError(String.format("Expected reference type %s, but got %s. ",
                                valueClass.getName(), varType.getName()));
            }
            return (ReferenceField<V>) reference;
        }

        private <T> T get(int index, Class<T> fieldClass) throws IncompatibleClassChangeError {
            if (index >= updaters.length) {
                throw incompatibleAccessError(
                                String.format("Expected number of updaters %s, but got %s. ",
                                                index + 1, updaters.length));
            } else if (updaters[index].getClass() != fieldClass) {
                throw incompatibleAccessError(
                                String.format("Expected field type %s, but got %s. ",
                                                fieldClass, updaters[index].getClass()));
            }
            return fieldClass.cast(updaters[index]);
        }

        private static IncompatibleClassChangeError incompatibleAccessError(String detailMessage) {
            return new IncompatibleClassChangeError(
                            String.format("Node inlining specification has changed in an incompatible way. %sRecompilation from source may solve this problem.",
                                            detailMessage));
        }

        private static boolean isReferenceField(Class<?> fieldClass) {
            return fieldClass == ReferenceField.class;
        }

        private static boolean isStateField(Class<?> fieldClass) {
            return fieldClass == StateField.class;
        }

        private static boolean isPrimitiveField(Class<?> fieldClass) {
            return !isReferenceField(fieldClass) && !isStateField(fieldClass);
        }

        /**
         * Creates an inline target for an inlined node. Intended for use by generated code only.
         *
         * @since 23.0
         */
        public static InlineTarget create(Class<?> targetClass, InlinableField... updaters) {
            Objects.requireNonNull(targetClass);
            Objects.requireNonNull(updaters);
            for (InlinableField updater : updaters) {
                Objects.requireNonNull(updater);
            }
            return new InlineTarget(targetClass, updaters);
        }

    }

    /**
     * Base class for inlined field references.
     *
     * @since 23.0
     */
    /*
     * Swap the super class to switch between VarHandleField and UnsafeField.
     *
     * SVM is not yet ready for the VarHandle implementation.
     */
    @SuppressWarnings({"static-method"})
    public abstract static class InlinableField extends UnsafeField {

        InlinableField(Class<?> receiverClass, Class<?> declaringClass, Lookup declaringLookup, String fieldName, Class<?> valueClass) {
            super(receiverClass, declaringClass, declaringLookup, fieldName, valueClass);
        }

        InlinableField(InlinableField prev) {
            super(prev);
        }

        /**
         * Validates a receiver of an inlined field. This is used for generated DSL code to fail
         * early for usage mistakes.
         *
         * @since 23.0
         */
        public final boolean validate(Node node) {
            // this receiver class is more precise than the
            // var handle type, so this produces better errors.
            resolveReceiver(node);
            // return boolean for convenient use in assertions
            return true;
        }

        static RuntimeException invalidValue(Class<?> expectedClass, Object value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            String message = String.format("Invalid parameter type passed to set. Instance of type '%s' expected but was '%s'. ",
                            getEnclosingSimpleName(expectedClass), value != null ? getEnclosingSimpleName(value.getClass()) : "null");
            throw new IllegalArgumentException(message);
        }

    }

    /**
     * Represents a field for updating state fields in inlined nodes.
     *
     * @since 23.0
     */
    public static final class StateField extends InlinableField {

        final int bitOffset;
        final int bitLength;
        final int bitMask;

        StateField(Lookup declaringLookup, String fieldName, int offset, int length) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, int.class);
            this.bitOffset = offset;
            this.bitLength = length;
            this.bitMask = computeMask(offset, length);
        }

        StateField(StateField prev, int offset, int length) {
            super(prev);
            this.bitOffset = prev.bitOffset + offset;
            this.bitLength = length;
            this.bitMask = computeMask(bitOffset, length);
        }

        private static int computeMask(int offset, int length) {
            int mask = 0;
            for (int i = offset; i < offset + length; i++) {
                mask |= 1 << i;
            }
            return mask;
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public StateField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * Creates a sub updater for a subset of bits in a state field. This method is intended to
         * be used by DSL-generated code only.
         *
         * @since 23.0
         */
        public StateField subUpdater(int newOffset, int newLength) {
            if (newOffset < 0) {
                throw new IllegalArgumentException("New offset parameter must not be negative.");
            } else if (newOffset + newLength > this.bitLength) {
                throw new IllegalArgumentException("Illegal new length parameter must not exceed the available bit length.");
            } else if (newLength <= 0) {
                throw new IllegalArgumentException("Invalid new length.");
            } else if (newOffset == 0 && newLength == this.bitLength) {
                return this;
            } else {
                return new StateField(this, newOffset, newLength);
            }
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public int get(Node node) {
            return (getInt(node) & bitMask) >>> bitOffset;
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, int value) {
            assert noBitsLost(value);
            Object receiver = node;
            int newState = getInt(receiver) & ~bitMask | ((value << bitOffset) & bitMask);
            setInt(receiver, newState);
        }

        private boolean noBitsLost(int providedBits) {
            int writtenBits = ((providedBits << bitOffset) & bitMask) >>> bitOffset;
            if (writtenBits != providedBits) {
                throw new IllegalArgumentException(
                                String.format("Bits lost in masked state updater set. Provided bits: 0x%s Written bits: 0x%s. " +
                                                "This could indicate a bug in subUpdater indices in the node object inlining logic.",
                                                Integer.toHexString(providedBits),
                                                Integer.toHexString(writtenBits)));
            }
            return true;
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static StateField create(Lookup declaringLookup, String field) {
            return new StateField(declaringLookup, field, 0, 32);
        }

    }

    /**
     * Represents a field for references in inlined nodes.
     *
     * @since 23.0
     */
    public static final class ReferenceField<T> extends InlinableField {

        final Class<?> valueClass;

        ReferenceField(Class<?> receiverClass, Class<?> lookupFieldClass, Lookup declaringLookup, String fieldName, Class<T> valueClass) {
            super(receiverClass, lookupFieldClass, declaringLookup, fieldName, valueClass);
            this.valueClass = valueClass;
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public ReferenceField<T> createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        @SuppressWarnings("unchecked")
        public T get(Node node) {
            return (T) getObject(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, T value) {
            setObject(node, value, valueClass);
        }

        /**
         * This method returns the value of the target field given a target node using volatile
         * semantics. The node parameter must match the class the field was created with. If the
         * type is not compatible, an {@link ClassCastException} is thrown. If <code>null</code> is
         * provided, then a {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        @SuppressWarnings("unchecked")
        public T getVolatile(Node node) {
            return (T) getObjectVolatile(node);
        }

        /**
         * This method sets the value of the target field giving the a target node and expected
         * value using compare and set semantics. The node parameter must match the class the field
         * was created with. If the type is not compatible, an {@link ClassCastException} is thrown.
         * If <code>null</code> is provided, then a {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public boolean compareAndSet(Node node, T expect, T update) {
            return compareAndSetObject(node, expect, update, valueClass);
        }

        /**
         * This method creates a new field given a lookup class, field name and value class. The
         * lookup class requires access to the field and must be directly accessible. If the field
         * is not found or the field type is not compatible, then an
         * {@link IllegalArgumentException} is thrown. The given field must not be final. This
         * method is intended to be used by DSL-generated code only.
         *
         * @since 23.0
         */
        @SuppressWarnings({"cast", "rawtypes", "unchecked"})
        public static <T> ReferenceField<T> create(Lookup declaringLookup, String field, Class<? super T> valueClass) {
            Class<?> lookupClass = declaringLookup.lookupClass();
            return (ReferenceField<T>) new ReferenceField(lookupClass, lookupClass, declaringLookup, field, valueClass);
        }
    }

    /**
     * Represents a field for boolean primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class BooleanField extends InlinableField {

        BooleanField(Lookup lookup, String fieldName) {
            super(lookup.lookupClass(), lookup.lookupClass(), lookup, fieldName, boolean.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public BooleanField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public boolean get(Node node) {
            return getBoolean(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, boolean value) {
            setBoolean(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static BooleanField create(Lookup declaringLookup, String field) {
            return new BooleanField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for byte primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class ByteField extends InlinableField {

        ByteField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, byte.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public ByteField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public byte get(Node node) {
            return getByte(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, byte value) {
            setByte(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static ByteField create(Lookup declaringLookup, String field) {
            return new ByteField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for short primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class ShortField extends InlinableField {

        ShortField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, short.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public ShortField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public short get(Node node) {
            return getShort(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, short value) {
            setShort(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static ShortField create(Lookup declaringLookup, String field) {
            return new ShortField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for char primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class CharField extends InlinableField {

        CharField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, char.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public CharField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public char get(Node node) {
            return getChar(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, char value) {
            setChar(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static CharField create(Lookup declaringLookup, String field) {
            return new CharField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for float primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class FloatField extends InlinableField {

        FloatField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, float.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public FloatField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public float get(Node node) {
            return getFloat(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, float value) {
            setFloat(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static FloatField create(Lookup declaringLookup, String field) {
            return new FloatField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for int primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class IntField extends InlinableField {

        IntField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, int.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public IntField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public int get(Node node) {
            return getInt(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, int value) {
            setInt(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static IntField create(Lookup declaringLookup, String field) {
            return new IntField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for long primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class LongField extends InlinableField {

        LongField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, long.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public LongField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public long get(Node node) {
            return getLong(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, long value) {
            setLong(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static LongField create(Lookup declaringLookup, String field) {
            return new LongField(declaringLookup, field);
        }
    }

    /**
     * Represents a field for double primitives in inlined nodes.
     *
     * @since 23.0
     */
    public static final class DoubleField extends InlinableField {

        DoubleField(Lookup declaringLookup, String fieldName) {
            super(declaringLookup.lookupClass(), declaringLookup.lookupClass(), declaringLookup, fieldName, double.class);
        }

        /**
         * This method creates a parent accessor field. A parent accessor allows access to a field
         * through a parent pointer. The given class must exactly match the given receiver. This
         * method is intended to be used by the DSL-generated code.
         *
         * @since 23.0
         * @deprecated in 23.1 - no longer needed
         */
        @Deprecated
        public DoubleField createParentAccessor(@SuppressWarnings("unused") Class<? extends Node> parentClass) {
            return this;
        }

        /**
         * This method returns the value of the target field given a target node. The node parameter
         * must match the class the field was created with. If the type is not compatible, an
         * {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public double get(Node node) {
            return getDouble(node);
        }

        /**
         * This method sets the value of the target field giving the a target node. The node
         * parameter must match the class the field was created with. If the type is not compatible,
         * an {@link ClassCastException} is thrown. If <code>null</code> is provided, then a
         * {@link NullPointerException} is thrown.
         *
         * @since 23.0
         */
        public void set(Node node, double value) {
            setDouble(node, value);
        }

        /**
         * This method creates a new field given a lookup class and a field name. The lookup class
         * requires access to the field and must be directly accessible. If the field is not found
         * or the field type is not compatible, then an {@link IllegalArgumentException} is thrown.
         * The given field must not be final. This method is intended to be used by DSL-generated
         * code only.
         *
         * @since 23.0
         */
        public static DoubleField create(Lookup declaringLookup, String field) {
            return new DoubleField(declaringLookup, field);
        }
    }

    /**
     * Unsafe base class for fields.
     */
    @SuppressWarnings({"unused", "deprecation"})
    abstract static class UnsafeField {

        // used for TruffleBaseFeature substitution
        final Class<?> declaringClass;
        // used for TruffleBaseFeature substitution
        final String name;

        // used for precise checking -> exact type
        final Class<?> receiverClass;
        final long offset;

        UnsafeField(UnsafeField prev) {
            this.offset = prev.offset;
            this.receiverClass = prev.receiverClass;
            this.declaringClass = prev.declaringClass;
            this.name = prev.name;
        }

        UnsafeField(Class<?> receiverClass, Class<?> declaringClass, Lookup declaringLookup, String fieldName, Class<?> valueClass) {
            Objects.requireNonNull(receiverClass);
            Objects.requireNonNull(declaringClass);
            Objects.requireNonNull(declaringLookup);
            Objects.requireNonNull(fieldName);
            Objects.requireNonNull(valueClass);

            Field field;
            Class<?> fieldClass;
            try {
                this.declaringClass = declaringClass;
                this.name = fieldName;
                field = java.security.AccessController.doPrivileged(
                                new PrivilegedExceptionAction<Field>() {
                                    public Field run() throws NoSuchFieldException {
                                        if (declaringLookup == null) {
                                            return null;
                                        }
                                        return declaringClass.getDeclaredField(fieldName);
                                    }
                                });
                fieldClass = field.getType();
            } catch (PrivilegedActionException pae) {
                if (pae.getException() instanceof NoSuchFieldException) {
                    throw new IllegalArgumentException(String.format("No such field %s.%s.", declaringClass.getName(), fieldName), pae);
                }
                throw new AssertionError(pae.getException());
            }
            if (!fieldClass.isAssignableFrom(valueClass)) {
                throw new IllegalArgumentException(String.format("Expected field type %s, but got %s. ",
                                valueClass.getName(), fieldClass.getName()));
            }

            if (!declaringClass.isAssignableFrom(receiverClass)) {
                throw new AssertionError(String.format("Receiver class %s is not assignable to the declaring class %s.",
                                receiverClass.getName(),
                                declaringClass.getName()));
            }
            final int modifiers = field.getModifiers();
            if (Modifier.isFinal(modifiers)) {
                throw new IllegalArgumentException("Must not be final field");
            }
            this.receiverClass = receiverClass;
            this.offset = U.objectFieldOffset(field);
        }

        final Object resolveReceiver(Object node) {
            CompilerAsserts.partialEvaluationConstant(this);
            CompilerAsserts.partialEvaluationConstant(node);
            Object value;
            // trigger implicit NPE here
            if (node.getClass() == receiverClass) {
                // fast common path
                value = node;
            } else {
                // slow path with parent resolve
                value = resolveReceiverSlow(node);
            }
            return receiverClass.cast(value);
        }

        /**
         * Looks up the parent {@link SpecializationDataNode} instances to find the correct
         * receiver.
         */
        @ExplodeLoop
        private Object resolveReceiverSlow(Object node) {
            if (receiverClass.isInstance(node)) {
                /*
                 * if the receiver type does not happen to be exact, handle this here to not slow
                 * down the fast-path.
                 */
                return node;
            }
            Object receiver = node;
            while (receiver != null) {
                assert validateForParentLookup(node, receiver);
                receiver = U.getObject(receiver, PARENT.offset);
                if (receiverClass.isInstance(receiver)) {
                    break;
                }
            }

            if (receiver == null) {
                throw nullError(node);
            }
            return receiver;
        }

        /**
         * Validates that we are allowed to do a parent lookup for the inline context. Parent
         * lookups are only allowed for all instances of {@link SpecializationDataNode} generated by
         * the DSL. Any further parent lookups are invalid.
         */
        @TruffleBoundary
        private boolean validateForParentLookup(Object inlineTarget, Object parent) {
            if (CompilerDirectives.inCompiledCode()) {
                return true;
            }
            if (parent == null) {
                return true;
            }
            /*
             * If the inline context is a specialization data node we can check that all consecutive
             * nodes in the parent chain are specialization data classes too. If code has not yet
             * been recompiled with the latest DSL version we do not provide bad errors.
             */
            if (inlineTarget instanceof SpecializationDataNode) {
                if (!(parent instanceof SpecializationDataNode)) {
                    throw invalidReceiver(inlineTarget);
                }
            }
            return true;
        }

        private RuntimeException nullError(Object node) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (node == null) {
                throw nullReceiver(node);
            } else {
                throw invalidReceiver(node);
            }
        }

        private NullPointerException nullReceiver(Object node) {
            CompilerAsserts.neverPartOfCompilation();
            return new NullPointerException(String.format(
                            "Invalid inline context node passed to an inlined field. A receiver of type '%s' was expected but is null. " +
                                            "Did you pass the wrong node to an execute method of an inlined cached node?",
                            getEnclosingSimpleName(receiverClass)));
        }

        private RuntimeException invalidReceiver(Object inlineTarget) {
            CompilerAsserts.neverPartOfCompilation();
            throw new ClassCastException(String.format("Invalid inline context node passed to an inlined field. A receiver of type '%s' was expected but is '%s'. " + //
                            "Did you pass the wrong node to an execute method of an inlined cached node?",
                            getEnclosingSimpleName(receiverClass), getEnclosingSimpleName(inlineTarget.getClass())));
        }

        final boolean getBoolean(Object node) {
            return U.getBoolean(resolveReceiver(node), offset);
        }

        final byte getByte(Object node) {
            return U.getByte(resolveReceiver(node), offset);
        }

        final short getShort(Object node) {
            return U.getShort(resolveReceiver(node), offset);
        }

        final char getChar(Object node) {
            return U.getChar(resolveReceiver(node), offset);
        }

        final int getInt(Object node) {
            return U.getInt(resolveReceiver(node), offset);
        }

        final float getFloat(Object node) {
            return U.getFloat(resolveReceiver(node), offset);
        }

        final long getLong(Object node) {
            return U.getLong(resolveReceiver(node), offset);
        }

        final double getDouble(Object node) {
            return U.getDouble(resolveReceiver(node), offset);
        }

        final Object getObject(Object node) {
            return U.getObject(resolveReceiver(node), offset);
        }

        final void setBoolean(Object node, boolean v) {
            U.putBoolean(resolveReceiver(node), offset, v);
        }

        final void setByte(Object node, byte v) {
            U.putByte(resolveReceiver(node), offset, v);
        }

        final void setShort(Object node, short v) {
            U.putShort(resolveReceiver(node), offset, v);
        }

        final void setChar(Object node, char v) {
            U.putChar(resolveReceiver(node), offset, v);
        }

        final void setInt(Object node, int v) {
            U.putInt(resolveReceiver(node), offset, v);
        }

        final void setFloat(Object node, float v) {
            U.putFloat(resolveReceiver(node), offset, v);
        }

        final void setLong(Object node, long v) {
            U.putLong(resolveReceiver(node), offset, v);
        }

        final void setDouble(Object node, double v) {
            U.putDouble(resolveReceiver(node), offset, v);
        }

        final void setObject(Object node, Object v, Class<?> valueClass) {
            if (!valueClass.isInstance(v) && v != null) {
                throw InlinableField.invalidValue(valueClass, v);
            }
            U.putObject(resolveReceiver(node), offset, v);
        }

        final Object getObjectVolatile(Object node) {
            return U.getObjectVolatile(resolveReceiver(node), offset);
        }

        final boolean compareAndSetObject(Object node, Object expect, Object update, Class<?> valueClass) {
            if (!valueClass.isInstance(update) && update != null) {
                throw InlinableField.invalidValue(valueClass, update);
            }
            return U.compareAndSwapObject(resolveReceiver(node), offset, expect, update);
        }

        private static sun.misc.Unsafe getUnsafe() {
            try {
                return sun.misc.Unsafe.getUnsafe();
            } catch (SecurityException e) {
            }
            try {
                Field theUnsafeInstance = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafeInstance.setAccessible(true);
                return (sun.misc.Unsafe) theUnsafeInstance.get(sun.misc.Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
            }
        }

        static final sun.misc.Unsafe U = getUnsafe();
    }

    /*
     * Dead code expected to be revitalized as soon as SVM supports better optimizations of var
     * handles.
     */
    abstract static class VarHandleField {

        private final Class<?> receiverClass;
        private final VarHandle handle;

        @SuppressWarnings("unused")
        VarHandleField(Class<?> receiverClass, Class<?> lookupClass, Lookup declaringLookup, String fieldName, Class<?> valueClass) {
            try {
                this.receiverClass = receiverClass;
                this.handle = declaringLookup.findVarHandle(lookupClass, fieldName, valueClass);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalArgumentException(e);
            }
        }

        VarHandleField(VarHandleField prev) {
            this.handle = prev.handle;
            this.receiverClass = prev.receiverClass;
        }

        Class<?> getFieldClass() {
            return handle.varType();
        }

        final boolean getBoolean(Object node) {
            return (boolean) handle.get(node);
        }

        final byte getByte(Object node) {
            return (byte) handle.get(node);
        }

        final short getShort(Object node) {
            return (short) handle.get(node);
        }

        final char getChar(Object node) {
            return (char) handle.get(node);
        }

        final int getInt(Object node) {
            return (int) handle.get(node);
        }

        final float getFloat(Object node) {
            return (float) handle.get(node);
        }

        final long getLong(Object node) {
            return (long) handle.get(node);
        }

        final double getDouble(Object node) {
            return (double) handle.get(node);
        }

        final void setBoolean(Object node, boolean v) {
            handle.set(node, v);
        }

        final void setByte(Object node, byte v) {
            handle.set(node, v);
        }

        final void setShort(Object node, short v) {
            handle.set(node, v);
        }

        final void setChar(Object node, char v) {
            handle.set(node, v);
        }

        final void setInt(Object node, int v) {
            handle.set(node, v);
        }

        final void setFloat(Object node, float v) {
            handle.set(node, v);
        }

        final void setLong(Object node, long v) {
            handle.set(node, v);
        }

        final void setDouble(Object node, double v) {
            handle.set(node, v);
        }

        final void setObject(Object node, Object v) {
            handle.set(node, v);
        }

        final Object getObject(Object node) {
            return handle.get(node);
        }

        final Object getObjectVolatile(Object node) {
            return handle.getVolatile(node);
        }

        final boolean compareAndSetObject(Object node, Object expect, Object update) {
            return handle.compareAndSet(node, expect, update);
        }

        @Override
        public final String toString() {
            StringBuilder b = new StringBuilder(getClass().getSimpleName());
            b.append("[");
            b.append(handle);
            b.append("]");
            return b.toString();
        }
    }

}
