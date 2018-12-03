/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public final class ConstantPool {

    public enum Tag {
        INVALID(0),
        UTF8(1),
        INTEGER(3),
        FLOAT(4),
        LONG(5),
        DOUBLE(6),
        CLASS(7),
        STRING(8),
        FIELD_REF(9),
        METHOD_REF(10),
        INTERFACE_METHOD_REF(11),
        NAME_AND_TYPE(12),
        METHODHANDLE(15),
        METHODTYPE(16),
        DYNAMIC(17),
        INVOKEDYNAMIC(18),
        MODULE(19),
        PACKAGE(20);

        public final byte value;

        /**
         * @param value
         */
        private Tag(int value) {
            assert (byte) value == value;
            this.value = (byte) value;
        }

        public int getValue() {
            return value;
        }

        static Tag fromValue(int value) {
            // @formatter:off
            switch (value) {
                case 1: return UTF8;
                case 3: return INTEGER;
                case 4: return FLOAT;
                case 5: return LONG;
                case 6: return DOUBLE;
                case 7: return CLASS;
                case 8: return STRING;
                case 9: return FIELD_REF;
                case 10: return METHOD_REF;
                case 11: return INTERFACE_METHOD_REF;
                case 12: return NAME_AND_TYPE;
                case 15: return METHODHANDLE;
                case 16: return METHODTYPE;
                case 17: return DYNAMIC;
                case 18: return INVOKEDYNAMIC;
                case 19: return MODULE;
                case 20: return PACKAGE;
                default: return null;
            }
            // @formatter:on
        }

        public static final List<Tag> VALUES = Arrays.asList(values());
    }

    @CompilationFinal(dimensions = 1) private final PoolConstant[] constants;

    private final EspressoContext context;

    public EspressoContext getContext() {
        return context;
    }

    private final StaticObject classLoader;

    /**
     * Creates a constant pool from a class file.
     */
    public ConstantPool(EspressoContext context, StaticObject classLoader, ClassfileStream stream, ClassfileParser parser) {
        this.context = context;
        final int length = stream.readU2();
        if (length < 1) {
            throw stream.classFormatError("Invalid constant pool size (" + length + ")");
        }

        final PoolConstant[] entries = new PoolConstant[length];
        entries[0] = InvalidConstant.VALUE;

        this.classLoader = classLoader;

        int i = 1;
        while (i < length) {
            final int tagByte = stream.readU1();
            final Tag tag = Tag.fromValue(tagByte);
            if (tag == null) {
                throw new ClassFormatError("Invalid constant pool entry type at index " + i);
            }
            switch (tag) {
                case CLASS: {
                    int classNameIndex = stream.readU2();
                    entries[i] = new ClassConstant.Index(classNameIndex);
                    break;
                }
                case STRING: {
                    entries[i] = new StringConstant(stream.readU2());
                    break;
                }
                case FIELD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new FieldRefConstant.Indexes(classIndex, nameAndTypeIndex);
                    break;
                }
                case METHOD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new ClassMethodRefConstant.Indexes(classIndex, nameAndTypeIndex);
                    break;
                }
                case INTERFACE_METHOD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new InterfaceMethodRefConstant.Indexes(classIndex, nameAndTypeIndex);
                    break;
                }
                case NAME_AND_TYPE: {
                    int nameIndex = stream.readU2();
                    int typeIndex = stream.readU2();
                    if (nameIndex < i && typeIndex < i) {
                        Utf8Constant name = (Utf8Constant) entries[nameIndex];
                        Utf8Constant type = (Utf8Constant) entries[typeIndex];
                        entries[i] = new NameAndTypeConstant.Resolved(name, type);
                    } else {
                        entries[i] = new NameAndTypeConstant.Indexes(nameIndex, typeIndex);
                    }
                    break;
                }
                case INTEGER: {
                    entries[i] = new IntegerConstant(stream.readS4());
                    break;
                }
                case FLOAT: {
                    entries[i] = new FloatConstant(stream.readFloat());
                    break;
                }
                case LONG: {
                    entries[i] = new LongConstant(stream.readS8());
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new ClassFormatError("Invalid long constant index " + (i - 1));
                    }
                    break;
                }
                case DOUBLE: {
                    entries[i] = new DoubleConstant(stream.readDouble());
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new ClassFormatError("Invalid double constant index " + (i - 1));
                    }
                    break;
                }
                case UTF8: {
                    entries[i] = context.getSymbolTable().make(stream.readString());
                    break;
                }
                case METHODHANDLE: {
                    parser.checkInvokeDynamicSupport(tag);
                    int refKind = stream.readU1();
                    int refIndex = stream.readU2();
                    entries[i] = new MethodHandleConstant.Index(refKind, refIndex);
                    break;
                }
                case METHODTYPE: {
                    parser.checkInvokeDynamicSupport(tag);
                    int descriptorIndex = stream.readU2();
                    entries[i] = new MethodTypeConstant.Index(descriptorIndex);
                    break;
                }
                case DYNAMIC: {
                    parser.checkDynamicConstantSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new DynamicConstant.Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    parser.updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                case INVOKEDYNAMIC: {
                    parser.checkInvokeDynamicSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = new InvokeDynamicConstant.Indexes(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    parser.updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                default: {
                    parser.handleBadConstant(tag, stream);
                    break;
                }
            }
            i++;
        }
        constants = entries;
    }

    public StaticObject getClassLoader() {
        return classLoader;
    }

    public PoolConstant at(int index) {
        return at(index, null);
    }

    public PoolConstant at(int index, String description) {
        try {
            return constants[index];
        } catch (IndexOutOfBoundsException exception) {
            throw verifyError("Constant pool index (" + index + ")" + (description == null ? "" : " for " + description) + " is out of range");
        }
    }

    /**
     * Updates the constant entry at a given index.
     */
    PoolConstant updateAt(int index, PoolConstant constant) {
        CompilerAsserts.neverPartOfCompilation();
        assert constant.tag() == constants[index].tag() : "cannot replace a " + constants[index].tag() + " with a " + constant.tag();
        constants[index] = constant;
        return constant;
    }

    /**
     * Gets the tag at a given index. If {@code index == 0} or there is no valid entry at
     * {@code index} (e.g. it denotes the slot following a double or long entry), then
     * {@link Tag#INVALID} is returned.
     */
    public Tag tagAt(int index) {
        try {
            return constants[index].tag();
        } catch (IndexOutOfBoundsException exception) {
            throw verifyError("Constant pool index " + index + " is out of range");
        }
    }

    static ClassFormatError unexpectedEntry(int index, Tag tag, String description, Tag... expected) {
        throw verifyError("Constant pool entry" + (description == null ? "" : " for " + description) + " at " + index + " is a " + tag + ", expected " + Arrays.toString(expected));
    }

    private ClassFormatError unexpectedEntry(int index, String description, Tag... expected) {
        throw unexpectedEntry(index, tagAt(index), description, expected);
    }

    static VerifyError verifyError(String message) {
        throw new VerifyError(message);
    }

    public int intAt(int index) {
        return intAt(index, null);
    }

    public int intAt(int index, String description) {
        try {
            final IntegerConstant constant = (IntegerConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, INTEGER);
        }
    }

    public long longAt(int index) {
        return longAt(index, null);
    }

    public long longAt(int index, String description) {
        try {
            final LongConstant constant = (LongConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, LONG);
        }
    }

    public float floatAt(int index) {
        return floatAt(index, null);
    }

    public float floatAt(int index, String description) {
        try {
            final FloatConstant constant = (FloatConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, FLOAT);
        }
    }

    public double doubleAt(int index) {
        return doubleAt(index, null);
    }

    public double doubleAt(int index, String description) {
        try {
            final DoubleConstant constant = (DoubleConstant) at(index);
            return constant.value();
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, DOUBLE);
        }
    }

    public NameAndTypeConstant nameAndTypeAt(int index) {
        return nameAndTypeAt(index, null);
    }

    public NameAndTypeConstant nameAndTypeAt(int index, String description) {
        try {
            return (NameAndTypeConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, NAME_AND_TYPE);
        }
    }

    public Utf8Constant utf8At(int index, String description) {
        return utf8ConstantAt(index, description);
    }

    public Utf8Constant utf8At(int index) {
        return utf8ConstantAt(index, null);
    }

    public ClassConstant classAt(int index) {
        return classAt(index, null);
    }

    public ClassConstant classAt(int index, String description) {
        try {
            return (ClassConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, CLASS);
        }
    }

    public MemberRefConstant memberAt(int index) {
        return memberAt(index, null);
    }

    public MemberRefConstant memberAt(int index, String description) {
        try {
            return (MemberRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, METHOD_REF, INTERFACE_METHOD_REF, FIELD_REF);
        }
    }

    public MethodRefConstant methodAt(int index) {
        try {
            return (MethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF, INTERFACE_METHOD_REF);
        }
    }

    public ClassMethodRefConstant classMethodAt(int index) {
        try {
            return (ClassMethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF);
        }
    }

    public InterfaceMethodRefConstant interfaceMethodAt(int index) {
        try {
            return (InterfaceMethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INTERFACE_METHOD_REF);
        }
    }

    public FieldRefConstant fieldAt(int index) {
        try {
            return (FieldRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, FIELD_REF);
        }
    }

    public Utf8Constant utf8ConstantAt(int index, String description) {
        try {
            return (Utf8Constant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, UTF8);
        }
    }

    public StringConstant stringConstantAt(int index) {
        try {
            return (StringConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, STRING);
        }
    }

    public InvokeDynamicConstant indyAt(int index) {
        try {
            return (InvokeDynamicConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INVOKEDYNAMIC);
        }
    }

    @Override
    public String toString() {
        Formatter buf = new Formatter();
        for (int i = 0; i < constants.length; i++) {
            PoolConstant c = constants[i];
            buf.format("#%d = %-15s // %s%n", i, c.tag(), c.toString(this, i));
        }
        return buf.toString();
    }

    public TypeDescriptor makeTypeDescriptor(String value) {
        return context.getLanguage().getTypeDescriptors().make(value);
    }

    public int length() {
        return constants.length;
    }

}
