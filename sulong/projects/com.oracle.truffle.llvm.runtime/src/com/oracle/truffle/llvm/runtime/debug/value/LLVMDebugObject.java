/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.debug.value;

import java.math.BigInteger;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebuggerValue;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceArrayLikeType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceBasicType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceForeignType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;

/**
 * This class describes a source-level variable. Debuggers can use it to display the original
 * source-level state of an executed LLVM IR file.
 */
@ExportLibrary(InteropLibrary.class)
public abstract class LLVMDebugObject extends LLVMDebuggerValue {

    private static final InteropLibrary UNCACHED_INTEROP = InteropLibrary.getFactory().getUncached();
    private static final String[] NO_KEYS = new String[0];

    protected final long offset;

    protected final LLVMDebugValue value;

    private final LLVMSourceType type;

    private final LLVMSourceLocation location;

    LLVMDebugObject(LLVMDebugValue value, long offset, LLVMSourceType type, LLVMSourceLocation location) {
        this.value = value;
        this.offset = offset;
        this.type = type;
        this.location = location;
    }

    /**
     * Get an object describing the referenced variable's type for the debugger to show.
     *
     * @return the type of the referenced object
     */
    public final LLVMSourceType getType() {
        return type;
    }

    @ExportMessage
    public final Object getMetaObject() throws UnsupportedMessageException {
        if (type == null) {
            throw UnsupportedMessageException.create();
        }
        return type;
    }

    @ExportMessage
    public final boolean hasMetaObject() {
        return type != null;
    }

    @ExportMessage
    final boolean hasSourceLocation() {
        return location != null;
    }

    @ExportMessage
    @TruffleBoundary
    final SourceSection getSourceLocation() {
        return location.getSourceSection();
    }

    public LLVMSourceLocation getDeclaration() {
        return location;
    }

    /**
     * If this is a complex object return the identifiers for its members.
     *
     * @return the keys or null
     */
    protected String[] getKeys() {
        if (value == null) {
            return NO_KEYS;

        } else {
            return getKeysSafe();
        }
    }

    protected abstract String[] getKeysSafe();

    /**
     * If this is a complex object return the member that is identified by the given key.
     *
     * @param identifier the object identifying the member
     *
     * @return the member or {@code null} if the key does not identify a member
     */
    private Object getMember(String identifier) {
        if (identifier == null) {
            return null;

        } else {
            return getMemberSafe(identifier);
        }
    }

    protected abstract Object getMemberSafe(String identifier);

    /**
     * Return an object that represents the value of the referenced variable.
     *
     * @return the value of the referenced variable
     */
    public Object getValue() {
        if (value == null) {
            return "";

        } else {
            return getValueSafe();
        }
    }

    protected abstract Object getValueSafe();

    /**
     * A representation of the current value of the referenced variable for the debugger to show.
     *
     * @return a string describing the referenced value
     */
    @Override
    @TruffleBoundary
    public String toString() {
        Object currentValue = getValue();

        if (LLVMManagedPointer.isInstance(currentValue)) {
            final LLVMManagedPointer managedPointer = LLVMManagedPointer.cast(currentValue);
            final Object target = managedPointer.getObject();

            String targetString;
            if (target instanceof LLVMFunctionDescriptor) {
                final LLVMFunctionDescriptor function = (LLVMFunctionDescriptor) target;
                targetString = "LLVM function " + function.getLLVMFunction().getName();

            } else {
                targetString = "<managed pointer>";
            }

            final long targetOffset = managedPointer.getOffset();
            if (targetOffset != 0L) {
                targetString = String.format("%s + %d byte%s", targetString, targetOffset, targetOffset == 1L ? "" : "s");
            }

            currentValue = targetString;
        }

        return Objects.toString(currentValue);
    }

    @Override
    protected int getElementCountForDebugger() {
        final String[] keys = getKeys();
        return keys == null ? 0 : keys.length;
    }

    @Override
    protected String[] getKeysForDebugger() {
        final String[] keys = getKeys();
        return keys != null ? keys : NO_KEYS;
    }

    @Override
    protected Object getElementForDebugger(String key) {
        return getMember(key);
    }

    private static final class Enum extends LLVMDebugObject {

        Enum(LLVMDebugValue value, long offset, LLVMSourceType type, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);
        }

        @Override
        protected Object getValueSafe() {
            final int size = (int) getType().getSize();

            final Object idRead = value.readBigInteger(offset, size, false);
            final BigInteger id;
            if (idRead instanceof BigInteger) {
                id = (BigInteger) idRead;
            } else {
                return value.describeValue(offset, size);
            }

            if (size >= Long.SIZE) {
                return LLVMDebugValue.toHexString(id);
            }

            final Object enumVal = getType().getElementName(id.longValue());
            return enumVal != null ? enumVal : LLVMDebugValue.toHexString(id);
        }

        @Override
        public String[] getKeysSafe() {
            return NO_KEYS;
        }

        @Override
        public Object getMemberSafe(String identifier) {
            return null;
        }
    }

    private static final class Structured extends LLVMDebugObject {

        private static final int STRING_MAX_LENGTH = 64;

        // in the order of their actual declaration in the containing type
        private final String[] memberIdentifiers;

        Structured(LLVMDebugValue value, long offset, LLVMSourceType type, String[] memberIdentifiers, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);
            this.memberIdentifiers = memberIdentifiers;
        }

        @Override
        public String[] getKeysSafe() {
            return memberIdentifiers;
        }

        @Override
        public Object getMemberSafe(String key) {
            final LLVMSourceType elementType = getType().getElementType(key);
            final long newOffset = this.offset + elementType.getOffset();
            final LLVMSourceLocation declaration = getType().getElementDeclaration(key);
            return create(elementType, newOffset, value, declaration);
        }

        @Override
        protected Object getValueSafe() {
            Object o = value.computeAddress(offset);

            final LLVMSourceType actualType = getType().getActualType();
            if (actualType instanceof LLVMSourceArrayLikeType) {
                final LLVMSourceType baseType = ((LLVMSourceArrayLikeType) actualType).getBaseType().getActualType();

                if (baseType instanceof LLVMSourceBasicType) {
                    switch (((LLVMSourceBasicType) baseType).getKind()) {
                        case UNSIGNED_CHAR:
                            o = appendString(o, false);
                            break;

                        case SIGNED_CHAR:
                            o = appendString(o, true);
                            break;
                    }
                }
            }

            return o;
        }

        private Object appendString(Object o, boolean signed) {
            if (getType().getElementCount() <= 0) {
                return o;
            }

            final StringBuilder sb = new StringBuilder();

            sb.append(o).append(" \"");

            final int numChars = Math.min(getType().getElementCount(), STRING_MAX_LENGTH);
            for (int i = 0; i < numChars; i++) {
                final LLVMSourceType elementType = getType().getElementType(i);
                final long newOffset = offset + elementType.getOffset();
                int size = (int) elementType.getSize();

                if (!value.canRead(newOffset, size)) {
                    sb.append("??");
                    continue;
                }

                final Object intRead = value.readBigInteger(newOffset, size, signed);
                if (intRead instanceof BigInteger) {
                    byte byteVal = ((BigInteger) intRead).byteValue();
                    char ch = signed ? (char) byteVal : (char) Byte.toUnsignedInt(byteVal);
                    if (ch == 0) {
                        break;
                    }
                    sb.append(ch);
                } else {
                    sb.append("??");
                }
            }

            if (numChars < getType().getElementCount()) {
                sb.append("... (+ ").append(getType().getElementCount() - numChars).append(" characters)");
            }

            sb.append("\"");

            return sb.toString();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Primitive extends LLVMDebugObject {

        Primitive(LLVMDebugValue value, long offset, LLVMSourceType type, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);
        }

        /**
         * Some special primitive debugger values like "unavailable" are represented as strings.
         */
        @ExportMessage
        @TruffleBoundary
        boolean isString() {
            Object v = getValue();
            return v instanceof String;
        }

        /**
         * Some special primitive debugger values like "unavailable" are represented as strings.
         */
        @ExportMessage
        @TruffleBoundary
        String asString() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof String) {
                return (String) v;
            } else {
                throw UnsupportedMessageException.create();
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean isNumber() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return true;
            } else {
                return UNCACHED_INTEROP.isNumber(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        @SuppressWarnings("unused")
        boolean fitsInByte() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                try {
                    byte b = ((BigInteger) v).byteValueExact();
                    return true;
                } catch (ArithmeticException e) {
                    return false;
                }
            } else {
                return UNCACHED_INTEROP.fitsInByte(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        @SuppressWarnings("unused")
        boolean fitsInShort() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                try {
                    short s = ((BigInteger) v).shortValueExact();
                    return true;
                } catch (ArithmeticException e) {
                    return false;
                }
            } else {
                return UNCACHED_INTEROP.fitsInShort(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        @SuppressWarnings("unused")
        boolean fitsInInt() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                try {
                    int i = ((BigInteger) v).intValueExact();
                    return true;
                } catch (ArithmeticException e) {
                    return false;
                }
            } else {
                return UNCACHED_INTEROP.fitsInInt(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        @SuppressWarnings("unused")
        boolean fitsInLong() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                try {
                    long l = ((BigInteger) v).longValueExact();
                    return true;
                } catch (ArithmeticException e) {
                    return false;
                }
            } else {
                return UNCACHED_INTEROP.fitsInLong(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean fitsInFloat() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return true;
            } else {
                return UNCACHED_INTEROP.fitsInFloat(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        boolean fitsInDouble() {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return true;
            } else {
                return UNCACHED_INTEROP.fitsInDouble(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        byte asByte() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return ((BigInteger) v).byteValue();
            } else {
                return UNCACHED_INTEROP.asByte(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        short asShort() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return ((BigInteger) v).shortValue();
            } else {
                return UNCACHED_INTEROP.asShort(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        int asInt() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return ((BigInteger) v).intValue();
            } else {
                return UNCACHED_INTEROP.asInt(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        long asLong() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return ((BigInteger) v).longValue();
            } else {
                return UNCACHED_INTEROP.asLong(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        float asFloat() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return ((BigInteger) v).floatValue();
            } else {
                return UNCACHED_INTEROP.asFloat(v);
            }
        }

        @ExportMessage
        @TruffleBoundary
        double asDouble() throws UnsupportedMessageException {
            Object v = getValue();
            if (v instanceof BigInteger) {
                return ((BigInteger) v).doubleValue();
            } else {
                return UNCACHED_INTEROP.asDouble(v);
            }
        }

        @Override
        public String[] getKeysSafe() {
            return NO_KEYS;
        }

        @Override
        public Object getMemberSafe(String identifier) {
            return null;
        }

        @Override
        public Object getValueSafe() {
            final int size = (int) getType().getSize();

            LLVMSourceType actualType = getType().getActualType();

            if (actualType instanceof LLVMSourceBasicType) {
                switch (((LLVMSourceBasicType) actualType).getKind()) {
                    case ADDRESS:
                        return value.readAddress(offset);

                    case BOOLEAN:
                        return value.readBoolean(offset);

                    case FLOATING:
                        return readFloating();

                    case SIGNED:
                        return value.readBigInteger(offset, size, true);

                    case SIGNED_CHAR: {
                        final Object intRead = value.readBigInteger(offset, size, true);
                        if (intRead instanceof BigInteger) {
                            return (char) ((BigInteger) intRead).byteValue();
                        } else {
                            return intRead;
                        }
                    }

                    case UNSIGNED:
                        return value.readBigInteger(offset, size, false);

                    case UNSIGNED_CHAR: {
                        final Object intRead = value.readBigInteger(offset, size, false);
                        if (intRead instanceof BigInteger) {
                            return (char) Byte.toUnsignedInt(((BigInteger) intRead).byteValue());
                        } else {
                            return intRead;
                        }
                    }
                }
            }

            return value.readUnknown(offset, size);
        }

        // clang uses the x86_fp80 datatype to represent the long double type which is indicated in
        // metadata to have 128 bits
        private static final int LONGDOUBLE_SIZE = 128;

        private Object readFloating() {
            final int size = (int) getType().getSize();
            try {
                switch (size) {
                    case Float.SIZE:
                        return value.readFloat(offset);

                    case Double.SIZE:
                        return value.readDouble(offset);

                    case LLVM80BitFloat.BIT_WIDTH:
                    case LONGDOUBLE_SIZE:
                        return value.read80BitFloat(offset);

                    default:
                        return value.readUnknown(offset, size);
                }
            } catch (IllegalStateException e) {
                CompilerDirectives.transferToInterpreter();
                return e.getMessage();
            }
        }
    }

    private static final class Pointer extends LLVMDebugObject {

        private static final String[] SAFE_DEREFERENCE_KEYS = new String[]{"<target>"};
        private static final String[] FOREIGN_KEYS = new String[]{"<foreign>", "<offset>"};

        private final LLVMSourcePointerType pointerType;

        Pointer(LLVMDebugValue value, long offset, LLVMSourceType type, LLVMSourceLocation declaration) {
            super(value, offset, type, declaration);

            LLVMSourceType actualType = getType().getActualType();
            if (actualType instanceof LLVMSourcePointerType) {
                this.pointerType = (LLVMSourcePointerType) actualType;
            } else {
                this.pointerType = null;
            }
        }

        private boolean isPointerToForeign() {
            if (value.isManagedPointer()) {
                Object base = value.getManagedPointerBase();
                return LLVMAsForeignLibrary.getFactory().getUncached().isForeign(base);
            } else {
                return false;
            }
        }

        @Override
        public String[] getKeysSafe() {
            if (pointerType != null && !pointerType.isReference() && (value.isAlwaysSafeToDereference(offset) || pointerType.isSafeToDereference())) {
                return SAFE_DEREFERENCE_KEYS;
            } else if (isPointerToForeign()) {
                return FOREIGN_KEYS;
            }
            final LLVMDebugObject target = dereference();
            return target == null ? NO_KEYS : target.getKeys();
        }

        @Override
        public Object getMemberSafe(String identifier) {
            if (FOREIGN_KEYS[0].equals(identifier)) {
                Object base = value.getManagedPointerBase();
                if (LLVMAsForeignLibrary.getFactory().getUncached().isForeign(base)) {
                    return LLVMAsForeignLibrary.getFactory().getUncached().asForeign(base);
                } else {
                    return "Cannot get foreign base pointer!";
                }

            } else if (FOREIGN_KEYS[1].equals(identifier)) {
                return value.getManagedPointerOffset();

            } else {
                final LLVMDebugObject target = dereference();
                if (target == null) {
                    return "Cannot dereference pointer!";
                }

                if (SAFE_DEREFERENCE_KEYS[0].equals(identifier)) {
                    assert pointerType != null;
                    assert !pointerType.isReference();
                    assert value.isAlwaysSafeToDereference(offset) || pointerType.isSafeToDereference();
                    return target;
                } else {
                    return target.getMember(identifier);
                }
            }
        }

        @Override
        protected Object getValueSafe() {
            if (isPointerToForeign()) {
                long o = offset + value.getManagedPointerOffset();
                if (o == 0) {
                    return "<foreign>";
                } else {
                    return String.format("<foreign> + %d byte", o);
                }
            } else if (pointerType == null || !pointerType.isReference()) {
                return value.readAddress(offset);
            } else {
                final LLVMDebugObject target = dereference();
                return target == null ? value.readAddress(offset) : target.getValue();
            }
        }

        private LLVMDebugObject dereference() {
            // the pointer may change at runtime, so we cannot just cache the dereferenced object
            if (pointerType == null || (!pointerType.isSafeToDereference() && !value.isAlwaysSafeToDereference(offset))) {
                return null;
            }

            final LLVMDebugValue targetValue = value.dereferencePointer(offset);
            if (targetValue == null) {
                return null;
            }
            return create(pointerType.getBaseType(), 0L, targetValue, null);
        }
    }

    private static final class StaticMembers extends LLVMDebugObject {

        StaticMembers(LLVMSourceStaticMemberType.CollectionType type) {
            super(LLVMDebugValue.UNAVAILABLE, 0L, type, null);
        }

        @Override
        public String[] getKeysSafe() {
            return ((LLVMSourceStaticMemberType.CollectionType) getType()).getIdentifiers();
        }

        @Override
        public Object getMemberSafe(String key) {
            final LLVMSourceType elementType = getType().getElementType(key);
            final LLVMSourceLocation declaration = getType().getElementDeclaration(key);
            final LLVMDebugObjectBuilder debugValue = ((LLVMSourceStaticMemberType.CollectionType) getType()).getMemberValue(key);
            return debugValue.getValue(elementType, declaration);
        }

        @Override
        public Object getValueSafe() {
            return "";
        }
    }

    private static final class Unsupported extends LLVMDebugObject {

        Unsupported(LLVMDebugValue value, long offset, LLVMSourceType type, LLVMSourceLocation location) {
            super(value, offset, type, location);
        }

        @Override
        protected String[] getKeysSafe() {
            return NO_KEYS;
        }

        @Override
        protected Object getMemberSafe(String identifier) {
            return null;
        }

        @Override
        protected Object getValueSafe() {
            return value.describeValue(0, 0);
        }
    }

    private static final class Foreign extends LLVMDebugObject {

        private static final String VALUE = "<interop value>";

        Foreign(LLVMDebugValue value, long offset, LLVMSourceType valueType, LLVMSourceLocation location) {
            super(value, offset, new LLVMSourceForeignType(valueType), location);
        }

        @Override
        protected String[] getKeysSafe() {
            return LLVMSourceForeignType.KEYS;
        }

        @Override
        protected Object getMemberSafe(String identifier) {
            if (!LLVMSourceForeignType.VALUE_KEY.equals(identifier)) {
                return null;
            }

            Object obj = value.asInteropValue();

            if (LLVMAsForeignLibrary.getFactory().getUncached().isForeign(obj)) {
                obj = LLVMAsForeignLibrary.getFactory().getUncached().asForeign(obj);
            }
            return obj;
        }

        @Override
        protected Object getValueSafe() {
            return VALUE;
        }
    }

    public static LLVMDebugObject create(LLVMSourceType type, long baseOffset, LLVMDebugValue value, LLVMSourceLocation declaration) {
        if (type.getActualType() == LLVMSourceType.UNKNOWN || type.getActualType() == LLVMSourceType.UNSUPPORTED) {
            return new Unsupported(value, baseOffset, LLVMSourceType.UNSUPPORTED, declaration);

        } else if (value != null && value.isInteropValue()) {
            return new Foreign(value, baseOffset, type, declaration);

        } else if (type.isAggregate()) {
            int elementCount = type.getElementCount();
            if (elementCount < 0) {
                // happens for dynamically initialized arrays
                elementCount = 0;
            }
            final String[] memberIdentifiers = new String[elementCount];
            for (int i = 0; i < elementCount; i++) {
                memberIdentifiers[i] = type.getElementName(i);
            }
            return new Structured(value, baseOffset, type, memberIdentifiers, declaration);

        } else if (type.isPointer()) {
            return new Pointer(value, baseOffset, type, declaration);

        } else if (type.isEnum()) {
            return new Enum(value, baseOffset, type, declaration);

        } else if (type instanceof LLVMSourceStaticMemberType.CollectionType) {
            return new StaticMembers((LLVMSourceStaticMemberType.CollectionType) type);

        } else {
            return new Primitive(value, baseOffset, type, declaration);
        }
    }
}
