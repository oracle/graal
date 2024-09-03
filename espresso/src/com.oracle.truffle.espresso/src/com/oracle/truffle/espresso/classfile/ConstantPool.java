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
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.MODULE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.NAME_AND_TYPE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.PACKAGE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.STRING;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.UTF8;

import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ClassMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DoubleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FloatConstant;
import com.oracle.truffle.espresso.classfile.constantpool.IntegerConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InterfaceMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvalidConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.LongConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MemberRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.ModifiedUTF8;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ClassLoadingEnv;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

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

    private static final DebugCounter UTF8_ENTRY_COUNT = DebugCounter.create("UTF8 Constant Pool entries");

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

        public final int getValue() {
            return value;
        }

        public final boolean isLoadable() {
            return loadable;
        }

        public static Tag fromValue(int value) {
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

        public boolean isValidForVersion(int major) {
            switch (this) {
                case UTF8:  // fall-through
                case INTEGER:  // fall-through
                case FLOAT:  // fall-through
                case LONG: // fall-through
                case DOUBLE: // fall-through
                case CLASS: // fall-through
                case STRING: // fall-through
                case FIELD_REF: // fall-through
                case METHOD_REF: // fall-through
                case INTERFACE_METHOD_REF: // fall-through
                case NAME_AND_TYPE: // fall-through
                    return major >= 45;
                case METHODHANDLE: // fall-through
                case METHODTYPE:
                case INVOKEDYNAMIC:
                    return major >= 51;
                case DYNAMIC:
                    return major >= 55;
                case MODULE:
                case PACKAGE:
                    return major >= 53;
                default:
                    throw EspressoError.shouldNotReachHere("Cannot validate tag version for" + this);
            }
        }

        public static final List<Tag> VALUES = Collections.unmodifiableList(Arrays.asList(values()));

    }

    public abstract int getMajorVersion();

    public abstract int getMinorVersion();

    public abstract int length();

    public abstract PoolConstant at(int index, String description);

    public final PoolConstant at(int index) {
        return at(index, null);
    }

    public abstract byte[] getRawBytes();

    static @JavaType(ClassFormatError.class) EspressoException unexpectedEntry(int index, ConstantPool.Tag tag, String description, ConstantPool.Tag... expected) {
        CompilerDirectives.transferToInterpreter();
        throw classFormatError("Constant pool entry" + (description == null ? "" : " for " + description) + " at " + index + " is a " + tag + ", expected " + Arrays.toString(expected));
    }

    final @JavaType(ClassFormatError.class) EspressoException unexpectedEntry(int index, String description, ConstantPool.Tag... expected) {
        CompilerDirectives.transferToInterpreter();
        throw unexpectedEntry(index, tagAt(index), description, expected);
    }

    static @JavaType(VerifyError.class) EspressoException verifyError(String message) {
        CompilerDirectives.transferToInterpreter();
        Meta meta = EspressoContext.get(null).getMeta();
        if (meta.java_lang_VerifyError == null) {
            throw EspressoError.fatal("VerifyError during early startup: ", message);
        }
        throw meta.throwExceptionWithMessage(meta.java_lang_VerifyError, message);
    }

    public static @JavaType(ClassFormatError.class) EspressoException classFormatError(String message) {
        CompilerDirectives.transferToInterpreter();
        Meta meta = EspressoContext.get(null).getMeta();
        if (meta.java_lang_ClassFormatError == null) {
            throw EspressoError.fatal("ClassFormatError during early startup: ", message);
        }
        throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, message);
    }

    static @JavaType(NoClassDefFoundError.class) EspressoException noClassDefFoundError(String message) {
        CompilerDirectives.transferToInterpreter();
        Meta meta = EspressoContext.get(null).getMeta();
        if (meta.java_lang_NoClassDefFoundError == null) {
            throw EspressoError.fatal("NoClassDefFoundError during early startup: ", message);
        }
        throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, message);
    }

    /**
     * Gets the tag at a given index. If {@code index == 0} or there is no valid entry at
     * {@code index} (e.g. it denotes the slot following a double or long entry), then
     * {@link ConstantPool.Tag#INVALID} is returned.
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

    public final <T> Symbol<T> symbolAt(int index) {
        return symbolAt(index, null);
    }

    @SuppressWarnings("unchecked")
    public final <T> Symbol<T> symbolAt(int index, String description) {
        try {
            final Utf8Constant constant = (Utf8Constant) at(index);
            return (Symbol<T>) constant.value();
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
            final StringConstant constant = (StringConstant) at(index);
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

    public final ClassConstant classAt(int index) {
        return classAt(index, null);
    }

    public final ClassConstant classAt(int index, String description) {
        try {
            return (ClassConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, CLASS);
        }
    }

    public final MemberRefConstant memberAt(int index) {
        return memberAt(index, null);
    }

    public final MemberRefConstant memberAt(int index, String description) {
        try {
            return (MemberRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, description, METHOD_REF, INTERFACE_METHOD_REF, FIELD_REF);
        }
    }

    public final MethodRefConstant methodAt(int index) {
        try {
            return (MethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF, INTERFACE_METHOD_REF);
        }
    }

    public final ClassMethodRefConstant classMethodAt(int index) {
        try {
            return (ClassMethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, METHOD_REF);
        }
    }

    public final InterfaceMethodRefConstant interfaceMethodAt(int index) {
        try {
            return (InterfaceMethodRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INTERFACE_METHOD_REF);
        }
    }

    public final FieldRefConstant fieldAt(int index) {
        try {
            return (FieldRefConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, FIELD_REF);
        }
    }

    public final StringConstant stringConstantAt(int index) {
        try {
            return (StringConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, STRING);
        }
    }

    public final InvokeDynamicConstant indyAt(int index) {
        try {
            return (InvokeDynamicConstant) at(index);
        } catch (ClassCastException e) {
            throw unexpectedEntry(index, null, INVOKEDYNAMIC);
        }
    }

    // endregion unresolved constants

    @Override
    public String toString() {
        Formatter buf = new Formatter();
        for (int i = 0; i < length(); i++) {
            PoolConstant c = at(i);
            buf.format("#%d = %-15s // %s%n", i, c.tag(), c.toString(this));
        }
        return buf.toString();
    }

    /**
     * Creates a constant pool from a class file.
     */
    public static ImmutableConstantPool parse(ClassLoadingEnv env, ClassfileStream stream, ClassfileParser parser, int majorVersion, int minorVersion) {
        return parse(env, stream, parser, null, majorVersion, minorVersion);
    }

    /**
     * Creates a constant pool from a class file.
     */
    public static ImmutableConstantPool parse(ClassLoadingEnv env, ClassfileStream stream, ClassfileParser parser, StaticObject[] patches, int majorVersion, int minorVersion) {
        final int length = stream.readU2();
        if (length < 1) {
            throw stream.classFormatError("Invalid constant pool size (" + length + ")");
        }
        int rawPoolStartPosition = stream.getPosition();
        final PoolConstant[] entries = new PoolConstant[length];
        entries[0] = InvalidConstant.VALUE;

        int i = 1;
        while (i < length) {
            final int tagByte = stream.readU1();
            final Tag tag = Tag.fromValue(tagByte);
            if (tag == null) {
                throw classFormatError("Invalid constant pool entry type at index " + i);
            }
            // check version for the tag except for module/package entries which must cause NDCFE
            if (!tag.isValidForVersion(parser.getMajorVersion()) && tag != MODULE && tag != PACKAGE) {
                throw classFormatError("Class file version does not support constant tag " + tagByte + " in class file");
            }
            switch (tag) {
                case CLASS: {
                    if (existsAt(patches, i)) {
                        StaticObject classSpecifier = patches[i];
                        int index = stream.readU2();
                        if (index == 0 || index >= length) {
                            throw classFormatError("Invalid Class constant index " + (i - 1));
                        }
                        if (classSpecifier.getKlass().getType() == Type.java_lang_Class) {
                            entries[i] = ClassConstant.preResolved(classSpecifier.getMirrorKlass());
                        } else {
                            entries[i] = ClassConstant.withString(env.getNames().lookup(env.getMeta().toHostString(patches[i])));
                        }

                        break;
                    }
                    int classNameIndex = stream.readU2();
                    entries[i] = ClassConstant.create(classNameIndex);
                    break;
                }
                case STRING: {
                    int index = stream.readU2();
                    if (index == 0 || index >= length) {
                        throw classFormatError("Invalid String constant index " + (i - 1));
                    }
                    if (existsAt(patches, i)) {
                        entries[i] = StringConstant.preResolved(patches[i]);
                    } else {
                        entries[i] = StringConstant.create(index);
                    }
                    break;
                }
                case FIELD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = FieldRefConstant.create(classIndex, nameAndTypeIndex);
                    break;
                }
                case METHOD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = ClassMethodRefConstant.create(classIndex, nameAndTypeIndex);
                    break;
                }
                case INTERFACE_METHOD_REF: {
                    int classIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = InterfaceMethodRefConstant.create(classIndex, nameAndTypeIndex);
                    break;
                }
                case NAME_AND_TYPE: {
                    int nameIndex = stream.readU2();
                    int typeIndex = stream.readU2();
                    entries[i] = NameAndTypeConstant.create(nameIndex, typeIndex);
                    break;
                }
                case INTEGER: {
                    if (existsAt(patches, i)) {
                        entries[i] = IntegerConstant.create(env.getMeta().unboxInteger(patches[i]));
                        stream.readS4();
                        break;
                    }
                    entries[i] = IntegerConstant.create(stream.readS4());
                    break;
                }
                case FLOAT: {
                    if (existsAt(patches, i)) {
                        entries[i] = FloatConstant.create(env.getMeta().unboxFloat(patches[i]));
                        stream.readFloat();
                        break;
                    }
                    entries[i] = FloatConstant.create(stream.readFloat());
                    break;
                }
                case LONG: {
                    if (existsAt(patches, i)) {
                        entries[i] = LongConstant.create(env.getMeta().unboxLong(patches[i]));
                        stream.readS8();
                    } else {
                        entries[i] = LongConstant.create(stream.readS8());
                    }
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw classFormatError("Invalid long constant index " + (i - 1));
                    }
                    break;
                }
                case DOUBLE: {
                    if (existsAt(patches, i)) {
                        entries[i] = DoubleConstant.create(env.getMeta().unboxDouble(patches[i]));
                        stream.readDouble();
                    } else {
                        entries[i] = DoubleConstant.create(stream.readDouble());
                    }
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw classFormatError("Invalid double constant index " + (i - 1));
                    }
                    break;
                }
                case UTF8: {
                    // Copy-less UTF8 constant.
                    // A new symbol is spawned (copy) only if doesn't already exists.
                    UTF8_ENTRY_COUNT.inc();
                    ByteSequence bytes = stream.readByteSequenceUTF();
                    entries[i] = env.getLanguage().getUtf8ConstantTable().getOrCreate(bytes);
                    break;
                }
                case METHODHANDLE: {
                    parser.checkInvokeDynamicSupport(tag);
                    int refKind = stream.readU1();
                    int refIndex = stream.readU2();
                    entries[i] = MethodHandleConstant.create(refKind, refIndex);
                    break;
                }
                case METHODTYPE: {
                    parser.checkInvokeDynamicSupport(tag);
                    entries[i] = MethodTypeConstant.create(stream.readU2());
                    break;
                }
                case DYNAMIC: {
                    parser.checkDynamicConstantSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = DynamicConstant.create(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    parser.updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                case INVOKEDYNAMIC: {
                    parser.checkInvokeDynamicSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = InvokeDynamicConstant.create(bootstrapMethodAttrIndex, nameAndTypeIndex);
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
        int rawPoolLength = stream.getPosition() - rawPoolStartPosition;

        ImmutableConstantPool constantPool = new ImmutableConstantPool(entries, majorVersion, minorVersion, rawPoolLength);

        if (parser.hasSeenBadConstant()) {
            return constantPool;
        }

        // Cannot faithfully reconstruct patched pools. obtaining raw pool of patched/hidden class
        // is meaningless anyway.
        assert patches != null || sameRawPool(constantPool, stream, rawPoolStartPosition, rawPoolLength);

        // Validation
        for (int j = 1; j < constantPool.length(); ++j) {
            entries[j].validate(constantPool);
        }

        return constantPool;
    }

    private static boolean sameRawPool(ConstantPool constantPool, ClassfileStream stream, int rawPoolStartPosition, int rawPoolLength) {
        return Arrays.equals(constantPool.getRawBytes(), stream.getByteRange(rawPoolStartPosition, rawPoolLength));
    }

    private static boolean existsAt(StaticObject[] patches, int index) {
        return patches != null && 0 <= index && index < patches.length && StaticObject.notNull(patches[index]);
    }
}
