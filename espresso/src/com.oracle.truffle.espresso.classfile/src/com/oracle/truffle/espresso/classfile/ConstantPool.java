/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.CLASS;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.DOUBLE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.FIELD_REF;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.FLOAT;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INTEGER;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INTERFACE_METHOD_REF;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.LONG;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.METHOD_REF;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.NAME_AND_TYPE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.STRING;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.UTF8;

import java.util.Arrays;
import java.util.Formatter;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DoubleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FloatConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ImmutablePoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.IntegerConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.LongConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MemberRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

/**
 * Immutable, shareable constant-pool representation.
 */
public abstract class ConstantPool {

    // @formatter:off
    public static final byte CONSTANT_Utf8               = 1;
    public static final byte CONSTANT_Integer            = 3;
    public static final byte CONSTANT_Float              = 4;
    public static final byte CONSTANT_Long               = 5;
    public static final byte CONSTANT_Double             = 6;
    public static final byte CONSTANT_Class              = 7;
    public static final byte CONSTANT_String             = 8;
    public static final byte CONSTANT_Fieldref           = 9;
    public static final byte CONSTANT_Methodref          = 10;
    public static final byte CONSTANT_InterfaceMethodref = 11;
    public static final byte CONSTANT_NameAndType        = 12;
    public static final byte CONSTANT_MethodHandle       = 15;
    public static final byte CONSTANT_MethodType         = 16;
    public static final byte CONSTANT_Dynamic            = 17;
    public static final byte CONSTANT_InvokeDynamic      = 18;
    public static final byte CONSTANT_Module             = 19;
    public static final byte CONSTANT_Package            = 20;
    // @formatter:on

    public enum Tag {
        INVALID(0),
        UTF8(CONSTANT_Utf8),
        INTEGER(CONSTANT_Integer, true),
        FLOAT(CONSTANT_Float, true),
        LONG(CONSTANT_Long, true),
        DOUBLE(CONSTANT_Double, true),
        CLASS(CONSTANT_Class, true),
        STRING(CONSTANT_String, true),
        FIELD_REF(CONSTANT_Fieldref),
        METHOD_REF(CONSTANT_Methodref),
        INTERFACE_METHOD_REF(CONSTANT_InterfaceMethodref),
        NAME_AND_TYPE(CONSTANT_NameAndType),
        METHODHANDLE(CONSTANT_MethodHandle, true),
        METHODTYPE(CONSTANT_MethodType, true),
        DYNAMIC(CONSTANT_Dynamic, true),
        INVOKEDYNAMIC(CONSTANT_InvokeDynamic),
        MODULE(CONSTANT_Module),
        PACKAGE(CONSTANT_Package);

        private final byte value;
        private final boolean loadable;

        Tag(int value) {
            this(value, false);
        }

        Tag(int value, boolean isLoadable) {
            assert (byte) value == value;
            this.value = (byte) value;
            this.loadable = isLoadable;
        }

        public final byte getValue() {
            return value;
        }

        public final boolean isLoadable() {
            return loadable;
        }

        public static Tag fromValue(int value) {
            return switch (value) {
                case 1 -> UTF8;
                case 3 -> INTEGER;
                case 4 -> FLOAT;
                case 5 -> LONG;
                case 6 -> DOUBLE;
                case 7 -> CLASS;
                case 8 -> STRING;
                case 9 -> FIELD_REF;
                case 10 -> METHOD_REF;
                case 11 -> INTERFACE_METHOD_REF;
                case 12 -> NAME_AND_TYPE;
                case 15 -> METHODHANDLE;
                case 16 -> METHODTYPE;
                case 17 -> DYNAMIC;
                case 18 -> INVOKEDYNAMIC;
                case 19 -> MODULE;
                case 20 -> PACKAGE;
                default -> null;
            };
        }

        public boolean isValidForVersion(int major) {
            return switch (this) {
                case INVALID -> true;
                case UTF8, INTEGER, FLOAT, LONG, DOUBLE, CLASS, STRING, FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF, NAME_AND_TYPE -> major >= 45;
                case METHODHANDLE, METHODTYPE, INVOKEDYNAMIC -> major >= 51;
                case DYNAMIC -> major >= 55;
                case MODULE, PACKAGE -> major >= 53;
            };
        }
    }

    public abstract int getMajorVersion();

    public abstract int getMinorVersion();

    public abstract int length();

    public abstract ImmutablePoolConstant at(int index, String description);

    public final ImmutablePoolConstant at(int index) {
        return at(index, null);
    }

    public abstract byte[] getRawBytes();

    @TruffleBoundary
    RuntimeException unexpectedEntry(int index, Tag tag, String description, Tag... expected) {
        throw classFormatError("Constant pool entry" + (description == null ? "" : " for " + description) + " at " + index + " is a " + tag + ", expected " + Arrays.toString(expected));
    }

    @TruffleBoundary
    RuntimeException unexpectedEntry(int index, String description, Tag... expected) {
        throw unexpectedEntry(index, tagAt(index), description, expected);
    }

    @TruffleBoundary
    public abstract RuntimeException classFormatError(String message);

    /**
     * Gets the tag at a given index. If {@code index == 0} or there is no valid entry at
     * {@code index} (e.g. it denotes the slot following a double or long entry), then
     * {@link Tag#INVALID} is returned.
     */
    public final Tag tagAt(int index) {
        return at(index).tag();
    }

    public final int intAt(int index) {
        return intAt(index, null);
    }

    public final int intAt(int index, String description) {
        try {
            final IntegerConstant constant = (IntegerConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, INTEGER);
        }
    }

    public final long longAt(int index) {
        return longAt(index, null);
    }

    public final long longAt(int index, String description) {
        try {
            final LongConstant constant = (LongConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, LONG);
        }
    }

    public final float floatAt(int index) {
        return floatAt(index, null);
    }

    public final float floatAt(int index, String description) {
        try {
            final FloatConstant constant = (FloatConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, FLOAT);
        }
    }

    public final double doubleAt(int index) {
        return doubleAt(index, null);
    }

    public final double doubleAt(int index, String description) {
        try {
            final DoubleConstant constant = (DoubleConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, DOUBLE);
        }
    }

    public final <T> Symbol<T> symbolAtUnsafe(int index) {
        return symbolAtUnsafe(index, null);
    }

    public final <T> Symbol<T> symbolAtUnsafe(int index, String description) {
        try {
            final Utf8Constant constant = (Utf8Constant) at(index);
            return constant.unsafeSymbolValue();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, UTF8);
        }
    }

    public final Utf8Constant utf8At(int index) {
        return utf8At(index, null);
    }

    public final Utf8Constant utf8At(int index, String description) {
        try {
            return (Utf8Constant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, UTF8);
        }
    }

    public final Symbol<ModifiedUTF8> stringAt(int index) {
        return stringAt(index, null);
    }

    public final Symbol<ModifiedUTF8> stringAt(int index, String description) {
        try {
            StringConstant.Index constant = (StringConstant.Index) at(index);
            return constant.getSymbol(this);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, STRING);
        }
    }

    // region unresolved constants

    public final NameAndTypeConstant nameAndTypeAt(int index) {
        return nameAndTypeAt(index, null);
    }

    public final NameAndTypeConstant nameAndTypeAt(int index, String description) {
        try {
            return (NameAndTypeConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, NAME_AND_TYPE);
        }
    }

    public final ClassConstant.ImmutableClassConstant classAt(int index) {
        return classAt(index, null);
    }

    public final ClassConstant.ImmutableClassConstant classAt(int index, String description) {
        try {
            return (ClassConstant.ImmutableClassConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, CLASS);
        }
    }

    public final MemberRefConstant.Indexes memberAt(int index) {
        return memberAt(index, null);
    }

    public final MemberRefConstant.Indexes memberAt(int index, String description) {
        try {
            return (MemberRefConstant.Indexes) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, METHOD_REF, INTERFACE_METHOD_REF, FIELD_REF);
        }
    }

    public final MethodRefConstant.Indexes methodAt(int index) {
        try {
            return (MethodRefConstant.Indexes) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF, INTERFACE_METHOD_REF);
        }
    }

    public final FieldRefConstant.Indexes fieldAt(int index) {
        try {
            return (FieldRefConstant.Indexes) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, FIELD_REF);
        }
    }

    public final InvokeDynamicConstant.Indexes indyAt(int index) {
        try {
            return (InvokeDynamicConstant.Indexes) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INVOKEDYNAMIC);
        }
    }

    // endregion unresolved constants

    @Override
    public String toString() {
        Formatter buf = new Formatter();
        for (int i = 0; i < length(); i++) {
            ImmutablePoolConstant c = at(i);
            buf.format("#%d = %-15s // %s%n", i, c.tag(), c.toString(this));
        }
        return buf.toString();
    }
}
