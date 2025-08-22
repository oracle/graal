/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.Constants.REF_LIMIT;
import static com.oracle.truffle.espresso.classfile.Constants.REF_NONE;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_getStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeInterface;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeStatic;
import static com.oracle.truffle.espresso.classfile.Constants.REF_invokeVirtual;
import static com.oracle.truffle.espresso.classfile.Constants.REF_newInvokeSpecial;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putField;
import static com.oracle.truffle.espresso.classfile.Constants.REF_putStatic;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Formatter;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.descriptors.Descriptor;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;

/**
 * Represents the constant pool of a Java class file, providing structured access to the various
 * constants defined in the pool.
 * <p>
 * The class provides type-safe methods to retrieve entries based on their expected types (e.g.,
 * {@link #intAt(int)}, {@link #utf8At(int)}), and performs validation to ensure entries conform to
 * the JVM specification.
 * <p>
 * Accessors are provided for specific entries, but there are common accessors, grouped in three
 * categories:
 * <p>
 * <b>Methods:</b> Entries tagged with {@link Tag#METHOD_REF} or {@link Tag#INTERFACE_METHOD_REF}
 * can be accessed using accessors prefixed with 'method' e.g. {@link ConstantPool#methodName(int)},
 * {@link ConstantPool#methodSignature(int)}
 * <p>
 * <b>Members:</b> Entries tagged with {@link Tag#FIELD_REF}, {@link Tag#METHOD_REF} or
 * {@link Tag#INTERFACE_METHOD_REF} can be accessed using accessors prefixed with 'member' e.g.
 * {@link ConstantPool#memberClassName(int)} (int)}, {@link ConstantPool#memberDescriptor(int)}
 * <p>
 * <b>BSM:</b> Entries tagged with {@link Tag#DYNAMIC}, {@link Tag#INVOKEDYNAMIC} can be accessed
 * using accessors prefixed with 'bsm' e.g. {@link ConstantPool#bsmDescriptor(int)},
 * {@link ConstantPool#bsmBootstrapMethodAttrIndex(int)}
 * 
 * @see Tag
 * @see Symbol
 * @see ParserConstantPool
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se23/html/jvms-4.html#jvms-4.4">JVM Spec
 *      - Constant Pool</a>
 */
public abstract class ConstantPool {

    // @formatter:off
    public static final byte CONSTANT_Invalid            = 0;
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

        public final boolean isMember() {
            return isMethod() || this == FIELD_REF;
        }

        public boolean isPrimitive() {
            return CONSTANT_Integer <= value && value <= CONSTANT_Double;
        }

        public final boolean isMethod() {
            return this == METHOD_REF || this == INTERFACE_METHOD_REF;
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

    @CompilationFinal(dimensions = 1) protected final byte[] tags;

    @CompilationFinal(dimensions = 1) protected final int[] entries;

    @CompilationFinal(dimensions = 1) protected final Symbol<?>[] symbols;

    protected final char majorVersion;
    protected final char minorVersion;

    private boolean validated;

    protected ConstantPool(byte[] tags, int[] entries, Symbol<?>[] symbols, int majorVersion, int minorVersion) {
        assert tags.length == entries.length;
        this.tags = tags;
        this.entries = entries;
        this.symbols = symbols;
        this.majorVersion = (char) majorVersion;
        this.minorVersion = (char) minorVersion;
    }

    protected ConstantPool(ParserConstantPool parserConstantPool) {
        this(parserConstantPool.tags, parserConstantPool.entries, parserConstantPool.symbols,
                        parserConstantPool.getMajorVersion(), parserConstantPool.getMinorVersion());
    }

    @TruffleBoundary
    public abstract RuntimeException classFormatError(String message);

    public abstract ParserConstantPool getParserConstantPool();

    public int getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public int length() {
        return entries.length;
    }

    public int intAt(int index) {
        checkTag(index, CONSTANT_Integer);
        return entries[index];
    }

    public float floatAt(int index) {
        checkTag(index, CONSTANT_Float);
        return Float.intBitsToFloat(entries[index]);
    }

    public long longAt(int index) {
        checkTag(index, CONSTANT_Long);
        assert byteTagAt(index + 1) == CONSTANT_Invalid;
        long hiBytes = entries[index];
        long loBytes = entries[index + 1];
        return (hiBytes << 32) | (loBytes & 0xFFFFFFFFL);
    }

    public double doubleAt(int index) {
        checkTag(index, CONSTANT_Double);
        assert byteTagAt(index + 1) == CONSTANT_Invalid;
        long hiBytes = entries[index];
        long loBytes = entries[index + 1];
        long rawBits = (hiBytes << 32) | (loBytes & 0xFFFFFFFFL);
        return Double.longBitsToDouble(rawBits);
    }

    @TruffleBoundary
    protected RuntimeException constantPoolIndexOutOfBounds(int index, String description) {
        throw classFormatError("Constant pool index (" + index + ")" + (description == null ? "" : " for " + description) + " is out of range");
    }

    protected byte byteTagAt(int index) {
        if (0 <= index && index < tags.length) {
            return tags[index];
        }
        throw constantPoolIndexOutOfBounds(index, null);
    }

    public Tag tagAt(int index) {
        byte byteTag = byteTagAt(index);
        if (byteTag == CONSTANT_Invalid) {
            return Tag.INVALID;
        }
        Tag tag = Tag.fromValue(byteTag);
        if (tag == null) {
            throw unexpectedEntry(index, "invalid tag");
        }
        return tag;
    }

    @TruffleBoundary
    protected RuntimeException unexpectedEntry(int index, Tag tag, String description, Tag... expected) {
        throw classFormatError("Constant pool entry" + (description == null ? "" : " for " + description) + " at " + index + " is a " + tag + ", expected " + Arrays.toString(expected));
    }

    @TruffleBoundary
    protected RuntimeException unexpectedEntry(int index, String description, Tag... expected) {
        throw unexpectedEntry(index, tagAt(index), description, expected);
    }

    protected void checkTag(int index, byte expectedTag) {
        byte tag = byteTagAt(index);
        if (tag != expectedTag) {
            throw unexpectedEntry(index, Tag.fromValue(tag), null, Tag.fromValue(expectedTag));
        }
    }

    private void checkMemberTag(int index) {
        byte tag = byteTagAt(index);
        if (tag != CONSTANT_Fieldref && tag != CONSTANT_Methodref && tag != CONSTANT_InterfaceMethodref) {
            throw unexpectedEntry(index, Tag.fromValue(tag), null, Tag.FIELD_REF, Tag.METHOD_REF, Tag.INTERFACE_METHOD_REF);
        }
    }

    private void checkBSMTag(int index) {
        byte tag = byteTagAt(index);
        if (tag != CONSTANT_InvokeDynamic && tag != CONSTANT_Dynamic) {
            throw unexpectedEntry(index, Tag.fromValue(tag), null, Tag.INVOKEDYNAMIC, Tag.DYNAMIC);
        }
    }

    private void checkMethodTag(int methodIndex) {
        byte tag = byteTagAt(methodIndex);
        if (tag != CONSTANT_Methodref && tag != CONSTANT_InterfaceMethodref) {
            throw unexpectedEntry(methodIndex, Tag.fromValue(tag), null, Tag.METHOD_REF, Tag.INTERFACE_METHOD_REF);
        }
    }

    @SuppressWarnings("unchecked")
    protected <T extends ModifiedUTF8> Symbol<T> unsafeUtf8At(int utf8Index) {
        return (Symbol<T>) utf8At(utf8Index);
    }

    public Symbol<?> utf8At(int utf8Index) {
        return utf8At(utf8Index, null);
    }

    public Symbol<?> utf8At(int utf8Index, String description) {
        checkTag(utf8Index, CONSTANT_Utf8);
        int symbolIndex = symbolEntryIndex(utf8Index);
        if (0 <= symbolIndex && symbolIndex < tags.length) {
            return symbols[symbolIndex];
        }
        throw classFormatError(description); // bad symbol index
    }

    /**
     * Return a string representation for the specified constant pool index. Assumes the constant
     * pool is validated.
     */
    public String toString(int index) {
        return switch (tagAt(index)) {
            case INVALID -> null;
            case UTF8 -> utf8At(index).toString();
            case INTEGER -> Integer.toString(intAt(index));
            case FLOAT -> Float.toString(floatAt(index));
            case LONG -> Long.toString(longAt(index));
            case DOUBLE -> Double.toString(doubleAt(index));
            case CLASS -> className(index).toString();
            case STRING -> utf8At(stringUtf8Index(index)).toString();
            case FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF ->
                className(memberClassIndex(index)) + "." + toString(memberNameAndTypeIndex(index));
            case NAME_AND_TYPE -> utf8At(nameAndTypeNameIndex(index)) + ":" + utf8At(nameAndTypeDescriptorIndex(index));
            case DYNAMIC, INVOKEDYNAMIC -> "#" + bsmBootstrapMethodAttrIndex(index) + " " + bsmName(index) + ":" + bsmDescriptor(index);
            case MODULE -> toString(moduleNameIndex(index));
            case PACKAGE -> toString(packageNameIndex(index));
            case METHODHANDLE -> methodHandleRefKind(index) + " " + toString(methodHandleMemberIndex(index));
            case METHODTYPE -> toString(methodTypeDescriptorIndex(index));
        };
    }

    protected int classNameIndex(int classIndex) {
        checkTag(classIndex, CONSTANT_Class);
        int nameIndex = entries[classIndex] & 0xFFFF;
        return nameIndex;
    }

    public Symbol<Name> className(int classIndex) {
        checkTag(classIndex, CONSTANT_Class);
        int nameIndex = entries[classIndex] & 0xFFFF;
        Symbol<Name> name = unsafeUtf8At(nameIndex);
        assert !validated || Validation.validClassNameEntry(name);
        return name;
    }

    private int symbolEntryIndex(int utf8Index) {
        checkTag(utf8Index, CONSTANT_Utf8);
        int symbolIndex = entries[utf8Index] & 0xFFFF;
        return symbolIndex;
    }

    protected int stringUtf8Index(int stringIndex) {
        checkTag(stringIndex, CONSTANT_String);
        int utf8Index = entries[stringIndex] & 0xFFFF;
        return utf8Index;
    }

    public int methodHandleRefKind(int methodHandleIndex) {
        checkTag(methodHandleIndex, CONSTANT_MethodHandle);
        int refKind = entries[methodHandleIndex] >>> 16;
        assert !validated || (REF_NONE < refKind && refKind < REF_LIMIT);
        return refKind;
    }

    protected int methodHandleMemberIndex(int methodHandleIndex) {
        checkTag(methodHandleIndex, CONSTANT_MethodHandle);
        int memberIndex = entries[methodHandleIndex] & 0xFFFF;
        return memberIndex;
    }

    protected int methodTypeDescriptorIndex(int methodTypeIndex) {
        checkTag(methodTypeIndex, CONSTANT_MethodType);
        int descriptorIndex = entries[methodTypeIndex] & 0xFFFF;
        return descriptorIndex;
    }

    @SuppressWarnings("unchecked")
    public Symbol<Signature> methodTypeSignature(int methodTypeIndex) {
        checkTag(methodTypeIndex, CONSTANT_MethodType);
        Symbol<?> signature = utf8At(methodTypeDescriptorIndex(methodTypeIndex));
        assert !validated || Validation.validSignatureDescriptor(signature);
        return (Symbol<Signature>) signature;
    }

    protected int invokeDynamicBootstrapMethodAttrIndex(int invokeDynamicIndex) {
        checkTag(invokeDynamicIndex, CONSTANT_InvokeDynamic);
        int bootstrapMethodAttrIndex = entries[invokeDynamicIndex] >>> 16;
        return bootstrapMethodAttrIndex;
    }

    protected int invokeDynamicNameAndTypeIndex(int invokeDynamicIndex) {
        checkTag(invokeDynamicIndex, CONSTANT_InvokeDynamic);
        int nameAndTypeIndex = entries[invokeDynamicIndex] & 0xFFFF;
        return nameAndTypeIndex;
    }

    public Symbol<Name> invokeDynamicName(int invokeDynamicIndex) {
        checkTag(invokeDynamicIndex, CONSTANT_InvokeDynamic);
        int nameAndTypeIndex = invokeDynamicNameAndTypeIndex(invokeDynamicIndex);
        return unsafeUtf8At(nameAndTypeNameIndex(nameAndTypeIndex));
    }

    public Symbol<Signature> invokeDynamicSignature(int invokeDynamicIndex) {
        checkTag(invokeDynamicIndex, CONSTANT_InvokeDynamic);
        int nameAndTypeIndex = invokeDynamicNameAndTypeIndex(invokeDynamicIndex);
        Symbol<Signature> signature = unsafeUtf8At(nameAndTypeDescriptorIndex(nameAndTypeIndex));
        assert !validated || Validation.validSignatureDescriptor(signature);
        return signature;
    }

    protected int bsmNameAndTypeIndex(int bsmIndex) {
        checkBSMTag(bsmIndex);
        int nameAndTypeIndex = entries[bsmIndex] & 0xFFFF;
        return nameAndTypeIndex;
    }

    public Symbol<Name> bsmName(int bsmIndex) {
        checkBSMTag(bsmIndex);
        int nameAndTypeIndex = bsmNameAndTypeIndex(bsmIndex);
        return unsafeUtf8At(nameAndTypeNameIndex(nameAndTypeIndex));
    }

    public Symbol<? extends Descriptor> bsmDescriptor(int bsmIndex) {
        checkBSMTag(bsmIndex);
        int nameAndTypeIndex = bsmNameAndTypeIndex(bsmIndex);
        return unsafeUtf8At(nameAndTypeDescriptorIndex(nameAndTypeIndex));
    }

    protected int dynamicBootstrapMethodAttrIndex(int dynamicIndex) {
        checkTag(dynamicIndex, CONSTANT_Dynamic);
        int bootstrapMethodAttrIndex = entries[dynamicIndex] >>> 16;
        return bootstrapMethodAttrIndex;
    }

    public int bsmBootstrapMethodAttrIndex(int bsmIndex) {
        checkBSMTag(bsmIndex);
        int bootstrapMethodAttrIndex = entries[bsmIndex] >>> 16;
        return bootstrapMethodAttrIndex;
    }

    protected int dynamicNameAndTypeIndex(int dynamicIndex) {
        checkTag(dynamicIndex, CONSTANT_Dynamic);
        int nameAndTypeIndex = entries[dynamicIndex] & 0xFFFF;
        return nameAndTypeIndex;
    }

    public Symbol<Name> dynamicName(int dynamicIndex) {
        checkTag(dynamicIndex, CONSTANT_Dynamic);
        int nameAndTypeIndex = dynamicNameAndTypeIndex(dynamicIndex);
        return unsafeUtf8At(nameAndTypeNameIndex(nameAndTypeIndex));
    }

    public Symbol<Type> dynamicType(int dynamicIndex) {
        checkTag(dynamicIndex, CONSTANT_Dynamic);
        int nameAndTypeIndex = dynamicNameAndTypeIndex(dynamicIndex);
        return unsafeUtf8At(nameAndTypeDescriptorIndex(nameAndTypeIndex));
    }

    protected int moduleNameIndex(int moduleIndex) {
        checkTag(moduleIndex, CONSTANT_Module);
        int nameIndex = entries[moduleIndex] & 0xFFFF;
        return nameIndex;
    }

    protected int packageNameIndex(int packageIndex) {
        checkTag(packageIndex, CONSTANT_Package);
        int nameIndex = entries[packageIndex] & 0xFFFF;
        return nameIndex;
    }

    protected int nameAndTypeNameIndex(int nameAndTypeIndex) {
        checkTag(nameAndTypeIndex, CONSTANT_NameAndType);
        int nameIndex = entries[nameAndTypeIndex] >>> 16;
        return nameIndex;
    }

    public Symbol<Name> nameAndTypeName(int nameAndTypeIndex) {
        return unsafeUtf8At(nameAndTypeNameIndex(nameAndTypeIndex));
    }

    public Symbol<? extends Descriptor> nameAndTypeDescriptor(int nameAndTypeIndex) {
        return unsafeUtf8At(nameAndTypeDescriptorIndex(nameAndTypeIndex));
    }

    public Symbol<Name> fieldName(int fieldIndex) {
        checkTag(fieldIndex, CONSTANT_Fieldref);
        return nameAndTypeName(memberNameAndTypeIndex(fieldIndex));
    }

    public Symbol<Type> fieldType(int fieldIndex) {
        checkTag(fieldIndex, CONSTANT_Fieldref);
        Symbol<Type> type = unsafeUtf8At(nameAndTypeDescriptorIndex(memberNameAndTypeIndex(fieldIndex)));
        assert !validated || Validation.validFieldDescriptor(type);
        return type;
    }

    public Symbol<Name> methodName(int methodIndex) {
        checkMethodTag(methodIndex);
        Symbol<Name> name = nameAndTypeName(memberNameAndTypeIndex(methodIndex));
        assert !validated || Validation.validMethodName(name);
        return name;
    }

    @SuppressWarnings("unchecked")
    public Symbol<Signature> methodSignature(int methodIndex) {
        checkMethodTag(methodIndex);
        Symbol<Signature> signature = (Symbol<Signature>) nameAndTypeDescriptor(memberNameAndTypeIndex(methodIndex));
        assert !validated || Validation.validSignatureDescriptor(signature);
        return signature;
    }

    protected int nameAndTypeDescriptorIndex(int nameAndTypeIndex) {
        checkTag(nameAndTypeIndex, CONSTANT_NameAndType);
        int descriptorIndex = entries[nameAndTypeIndex] & 0xFFFF;
        return descriptorIndex;
    }

    // TODO(peterssen): Do not expose holder index.
    public int memberClassIndex(int memberIndex) {
        checkMemberTag(memberIndex);
        int holderIndex = entries[memberIndex] >>> 16;
        return holderIndex;
    }

    public Symbol<Name> memberClassName(int memberIndex) {
        checkMemberTag(memberIndex);
        int holderIndex = entries[memberIndex] >>> 16;
        return className(holderIndex);
    }

    protected int memberNameAndTypeIndex(int memberIndex) {
        checkMemberTag(memberIndex);
        int nameAndTypeIndex = entries[memberIndex] & 0xFFFF;
        return nameAndTypeIndex;
    }

    public Symbol<Name> memberName(int memberIndex) {
        checkMemberTag(memberIndex);
        return nameAndTypeName(memberNameAndTypeIndex(memberIndex));
    }

    public Symbol<? extends Descriptor> memberDescriptor(int memberIndex) {
        checkMemberTag(memberIndex);
        return nameAndTypeDescriptor(memberNameAndTypeIndex(memberIndex));
    }

    /**
     * Validates the entry at the specified constant pool index. Validation of one entry may trigger
     * validation of other dependent entries. This method is resilient to invalid, ill-formed
     * constant pools, out of bounds indices, invalid tags and only throws
     * {@link ValidationException} if the specified entry or one of its dependencies is not valid.
     */
    public void validateConstantAt(int index, Tag expectedTag) throws ValidationException {
        validateTag(index, expectedTag.getValue());
        Tag tag = tagAt(index);
        switch (tag) {
            case INVALID -> {
                assert index == 0 || byteTagAt(index - 1) == CONSTANT_Long || byteTagAt(index - 1) == CONSTANT_Double;
            }
            case INTEGER, FLOAT -> {
                // OK
            }
            case LONG, DOUBLE -> validateTag(index + 1, CONSTANT_Invalid);
            case UTF8 -> {
                int symbolIndex = symbolEntryIndex(index);
                if (symbolIndex < 0 || symbols.length <= symbolIndex) {
                    throw ValidationException.raise("Bad symbol index for UTF8 entry: " + index);
                }
                utf8At(index).validateUTF8();
            }
            case CLASS -> {
                int utf8Index = classNameIndex(index);
                validateConstantAt(utf8Index, Tag.UTF8);
                utf8At(utf8Index).validateClassName();
            }
            case STRING -> {
                int utf8Index = stringUtf8Index(index);
                validateConstantAt(utf8Index, Tag.UTF8);
                utf8At(utf8Index).validateUTF8();
            }
            case FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF -> {
                int classIndex = memberClassIndex(index);
                validateConstantAt(classIndex, Tag.CLASS);
                int nameAndTypeIndex = memberNameAndTypeIndex(index);
                validateConstantAt(nameAndTypeIndex, Tag.NAME_AND_TYPE);
                int nameIndex = nameAndTypeNameIndex(nameAndTypeIndex);
                validateConstantAt(nameIndex, Tag.UTF8);
                int descriptorIndex = nameAndTypeDescriptorIndex(nameAndTypeIndex);
                validateConstantAt(descriptorIndex, Tag.UTF8);
                if (tag == Tag.FIELD_REF) {
                    utf8At(nameIndex).validateFieldName();
                    utf8At(descriptorIndex).validateType(false);
                } else {
                    Symbol<Name> name = utf8At(nameIndex).validateMethodName();
                    Symbol<Signature> signature = utf8At(descriptorIndex).validateSignature();
                    if (tag == Tag.METHOD_REF) {
                        /*
                         * If the name of the method of a CONSTANT_Methodref_info structure begins
                         * with a '<' ('\u003c'), then the name must be the special name <init>,
                         * representing an instance initialization method (&sect;2.9). The return
                         * type of such a method must be void.
                         */
                        if (name.length() > 0 && name.byteAt(0) == '<') {
                            if (!ParserNames._init_.equals(name) || !SignatureSymbols.returnsVoid(signature)) {
                                throw ValidationException.raise("<init> method must return void");
                            }
                        }
                    }
                }
            }
            case NAME_AND_TYPE -> {
                int nameIndex = nameAndTypeNameIndex(index);
                validateConstantAt(nameIndex, Tag.UTF8);
                int descriptorIndex = nameAndTypeDescriptorIndex(index);
                validateConstantAt(descriptorIndex, Tag.UTF8);
                Symbol<?> name = utf8At(nameIndex);
                Symbol<?> descriptor = utf8At(descriptorIndex);
                if (descriptor.length() > 0 && descriptor.byteAt(0) == '(') {
                    name.validateMethodName();
                    descriptor.validateSignature();
                } else {
                    // Fails with empty name.
                    name.validateFieldName();
                    descriptor.validateType(false);
                }
            }
            case METHODHANDLE -> {
                int refKind = methodHandleRefKind(index);
                int memberIndex = methodHandleMemberIndex(index);

                validateCPI(memberIndex);

                byte byteMemberTag = byteTagAt(memberIndex);
                if (byteMemberTag != CONSTANT_Fieldref && byteMemberTag != CONSTANT_Methodref && byteMemberTag != CONSTANT_InterfaceMethodref) {
                    throw ValidationException.raise("Ill-formed constant: " + tag);
                }
                Tag memberTag = tagAt(memberIndex);
                validateConstantAt(memberIndex, memberTag);

                int nameAndTypeIndex = memberNameAndTypeIndex(memberIndex);
                validateConstantAt(nameAndTypeIndex, Tag.NAME_AND_TYPE);

                Symbol<Name> memberName = nameAndTypeName(nameAndTypeIndex);
                if (ParserNames._clinit_.equals(memberName)) {
                    throw ValidationException.raise("Ill-formed constant: " + tag);
                }

                // If the value is 8 (REF_newInvokeSpecial), the name of the method represented by a
                // CONSTANT_Methodref_info structure must be <init>.
                if (ParserNames._init_.equals(memberName) && refKind != REF_newInvokeSpecial) {
                    throw ValidationException.raise("Ill-formed constant: " + tag);
                }

                if (!(REF_getField <= refKind && refKind <= REF_invokeInterface)) {
                    throw ValidationException.raise("Ill-formed constant: " + tag);
                }

                // If the value of the reference_kind item is 5 (REF_invokeVirtual), 6
                // (REF_invokeStatic), 7 (REF_invokeSpecial), or 9 (REF_invokeInterface), the name
                // of
                // the method represented by a CONSTANT_Methodref_info structure or a
                // CONSTANT_InterfaceMethodref_info structure must not be <init> or <clinit>.
                if (memberName.equals(ParserNames._init_) || memberName.equals(ParserNames._clinit_)) {
                    if (refKind == REF_invokeVirtual ||
                                    refKind == REF_invokeStatic ||
                                    refKind == REF_invokeSpecial ||
                                    refKind == REF_invokeInterface) {
                        throw ValidationException.raise("Ill-formed constant: " + tag);
                    }
                }

                boolean valid = false;
                switch (refKind) {
                    case REF_getField: // fall-through
                    case REF_getStatic: // fall-through
                    case REF_putField: // fall-through
                    case REF_putStatic:
                        /*
                         * If the value of the reference_kind item is 1 (REF_getField), 2
                         * (REF_getStatic), 3 (REF_putField), or 4 (REF_putStatic), then the
                         * constant_pool entry at that index must be a CONSTANT_Fieldref_info
                         * (&sect;4.4.2) structure representing a field for which a method handle is
                         * to be created.
                         */
                        valid = (memberTag == Tag.FIELD_REF);
                        break;
                    case REF_invokeVirtual: // fall-through
                    case REF_newInvokeSpecial:
                        /*
                         * If the value of the reference_kind item is 5 (REF_invokeVirtual) or 8
                         * (REF_newInvokeSpecial), then the constant_pool entry at that index must
                         * be a CONSTANT_Methodref_info structure (&sect;4.4.2) representing a
                         * class's method or constructor (&sect;2.9) for which a method handle is to
                         * be created.
                         */
                        valid = memberTag == Tag.METHOD_REF;
                        break;
                    case REF_invokeStatic: // fall-through
                    case REF_invokeSpecial:
                        /*
                         * If the value of the reference_kind item is 6 (REF_invokeStatic) or 7
                         * (REF_invokeSpecial), then if the class file version number is less than
                         * 52.0, the constant_pool entry at that index must be a
                         * CONSTANT_Methodref_info structure representing a class's method for which
                         * a method handle is to be created; if the class file version number is
                         * 52.0 or above, the constant_pool entry at that index must be either a
                         * CONSTANT_Methodref_info structure or a CONSTANT_InterfaceMethodref_info
                         * structure (&sect;4.4.2) representing a class's or interface's method for
                         * which a method handle is to be created.
                         */
                        valid = (memberTag == Tag.METHOD_REF) ||
                                        (getMajorVersion() >= ClassfileParser.JAVA_8_VERSION && memberTag == Tag.INTERFACE_METHOD_REF);
                        break;

                    case REF_invokeInterface:
                        /*
                         * If the value of the reference_kind item is 9 (REF_invokeInterface), then
                         * the constant_pool entry at that index must be a
                         * CONSTANT_InterfaceMethodref_info structure representing an interface's
                         * method for which a method handle is tobe created.
                         */
                        valid = (memberTag == Tag.INTERFACE_METHOD_REF);
                        break;
                }

                if (!valid) {
                    throw ValidationException.raise("Ill-formed constant: " + tag);
                }
            }
            case METHODTYPE -> {
                int descriptorIndex = methodTypeDescriptorIndex(index);
                validateConstantAt(descriptorIndex, Tag.UTF8);
                Symbol<?> descriptor = utf8At(descriptorIndex);
                descriptor.validateSignature();
            }
            case DYNAMIC, INVOKEDYNAMIC -> {
                int nameAndTypeIndex = bsmNameAndTypeIndex(index);
                validateConstantAt(nameAndTypeIndex, Tag.NAME_AND_TYPE);
                Symbol<?> name = nameAndTypeName(nameAndTypeIndex);
                Symbol<?> descriptor = nameAndTypeDescriptor(nameAndTypeIndex);
                if (tag == Tag.DYNAMIC) {
                    name.validateFieldName();
                    descriptor.validateType(false);
                } else {
                    name.validateMethodName();
                    descriptor.validateSignature();
                }
            }
            case MODULE -> {
                int nameIndex = moduleNameIndex(index);
                validateConstantAt(nameIndex, Tag.UTF8);
                Symbol<?> moduleName = utf8At(nameIndex, "module name");
                if (Validation.validModuleName(moduleName)) {
                    throw ValidationException.raise("Invalid module name: " + moduleName);
                }
            }
            case PACKAGE -> {
                int nameIndex = packageNameIndex(index);
                validateConstantAt(nameIndex, Tag.UTF8);
                Symbol<?> packageName = utf8At(nameIndex, "package name");
                if (Validation.validPackageName(packageName)) {
                    throw ValidationException.raise("Invalid package name: " + packageName);
                }
            }
        }
    }

    private void validateTag(int index, byte expectedTag) throws ValidationException {
        byte tag = byteTagAt(validateCPI(index));
        if (tag != expectedTag) {
            throw ValidationException.raise("Unexpected constant pool tag");
        }
    }

    private int validateCPI(int index) throws ValidationException {
        if (index < 0 || index >= length()) {
            throw ValidationException.raise("Constant pool index out of bounds");
        }
        return index;
    }

    public void validate() throws ValidationException {
        for (int i = 0; i < length(); ++i) {
            validateConstantAt(i);
        }
        validated = true;
    }

    public void validateConstantAt(int index) throws ValidationException {
        validateConstantAt(index, validateTag(index));
    }

    public Tag validateTag(int index) throws ValidationException {
        validateCPI(index);
        byte byteTag = byteTagAt(index);
        if (byteTag == 0) {
            return Tag.INVALID;
        }
        Tag tag = Tag.fromValue(byteTag);
        if (tag == null) {
            throw ValidationException.raise("Illegal constant pool tag");
        }
        return tag;
    }

    private void dumpRawBytes(ByteBuffer bb) {
        for (int i = 0; i < length(); ++i) {
            Tag tag = tagAt(i);
            if (tag == Tag.INVALID) {
                continue; // always skip INVALID entries
            }
            bb.put(byteTagAt(i));
            switch (tag) {
                case UTF8 -> {
                    Symbol<?> symbol = symbols[symbolEntryIndex(i)];
                    // u2 length
                    bb.putShort((short) symbol.length());
                    // bytes
                    symbol.writeTo(bb);
                }
                case INTEGER -> bb.putInt(intAt(i));
                case FLOAT -> bb.putFloat(floatAt(i));
                case LONG -> bb.putLong(longAt(i));
                case DOUBLE -> bb.putDouble(doubleAt(i));
                case CLASS -> bb.putShort((short) classNameIndex(i)); // u2 name_index
                case STRING -> bb.putShort((short) stringUtf8Index(i)); // u2 string_index
                case FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF -> {
                    // u2 class_index
                    bb.putShort((short) memberClassIndex(i));
                    // u2 name_and_type index
                    bb.putShort((short) memberNameAndTypeIndex(i));
                }
                case NAME_AND_TYPE -> {
                    // u2 name_index
                    bb.putShort((short) nameAndTypeNameIndex(i));
                    // u2 descriptor_index
                    bb.putShort((short) nameAndTypeDescriptorIndex(i));
                }
                case METHODHANDLE -> {
                    // u1 reference_kind
                    bb.put((byte) methodHandleRefKind(i));
                    // u2 reference_index
                    bb.putShort((short) methodHandleMemberIndex(i));
                }
                case DYNAMIC, INVOKEDYNAMIC -> {
                    // u2 bootstrap_method_attr_index
                    bb.putShort((short) bsmBootstrapMethodAttrIndex(i));
                    // u2 name_and_type_index
                    bb.putShort((short) bsmNameAndTypeIndex(i));
                }
                case METHODTYPE -> {
                    // u2 descriptor_index
                    bb.putShort((short) methodTypeDescriptorIndex(i));
                }
                case MODULE -> bb.putShort((short) moduleNameIndex(i)); // u2 name_index
                case PACKAGE -> bb.putShort((short) packageNameIndex(i)); // u2 name_index
            }
        }
    }

    public boolean isSame(int thisIndex, int otherIndex, ConstantPool otherPool) {
        Tag tag = tagAt(thisIndex);
        if (tag != otherPool.tagAt(otherIndex)) {
            return false;
        }
        return switch (tag) {
            case INVALID -> false; // TODO(peterssen): Check other is also invalid?
            case UTF8 -> utf8At(thisIndex) == otherPool.utf8At(otherIndex);
            case INTEGER -> intAt(thisIndex) == otherPool.intAt(otherIndex);
            case FLOAT -> Float.floatToRawIntBits(floatAt(thisIndex)) == Float.floatToRawIntBits(otherPool.floatAt(otherIndex));
            case LONG -> longAt(thisIndex) == otherPool.longAt(otherIndex);
            case DOUBLE -> Double.doubleToRawLongBits(doubleAt(thisIndex)) == Double.doubleToRawLongBits(otherPool.doubleAt(otherIndex));
            case CLASS -> isSame(classNameIndex(thisIndex), otherPool.classNameIndex(otherIndex), otherPool);
            case STRING -> isSame(stringUtf8Index(thisIndex), otherPool.stringUtf8Index(otherIndex), otherPool);

            case FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF ->
                isSame(memberClassIndex(thisIndex), otherPool.memberClassIndex(otherIndex), otherPool) // class
                                && isSame(memberNameAndTypeIndex(thisIndex), otherPool.memberNameAndTypeIndex(otherIndex), otherPool); // name_and_type

            case NAME_AND_TYPE ->
                isSame(nameAndTypeNameIndex(thisIndex), otherPool.nameAndTypeNameIndex(otherIndex), otherPool) &&
                                isSame(nameAndTypeDescriptorIndex(thisIndex), otherPool.nameAndTypeDescriptorIndex(otherIndex), otherPool);

            case METHODHANDLE ->
                methodHandleRefKind(thisIndex) == otherPool.methodHandleRefKind(otherIndex) && isSame(methodHandleMemberIndex(thisIndex), otherPool.methodHandleMemberIndex(otherIndex), otherPool);

            case METHODTYPE -> isSame(methodTypeDescriptorIndex(thisIndex), otherPool.methodTypeDescriptorIndex(otherIndex), otherPool);

            case DYNAMIC, INVOKEDYNAMIC ->
                isSame(bsmNameAndTypeIndex(thisIndex), otherPool.bsmNameAndTypeIndex(otherIndex), otherPool)
                // FIXME: comparison of the index is not enough
                                && bsmBootstrapMethodAttrIndex(thisIndex) == otherPool.bsmBootstrapMethodAttrIndex(otherIndex);

            case MODULE -> isSame(moduleNameIndex(thisIndex), otherPool.moduleNameIndex(otherIndex), otherPool);
            case PACKAGE -> isSame(packageNameIndex(thisIndex), otherPool.packageNameIndex(otherIndex), otherPool);
        };
    }

    @Override
    public String toString() {
        // TODO(peterssen): Remove Formatter? from Espresso (brings lots of code).
        Formatter buf = new Formatter();
        for (int i = 0; i < length(); i++) {
            buf.format("#%d = %-15s // %s%n", i, tagAt(i), toString(i));
        }
        return buf.toString();
    }

    public byte[] toRawBytes() {
        int rawBytesSize = computeRawBytesSize();
        byte[] bytes = new byte[rawBytesSize];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        dumpRawBytes(bb);
        assert !bb.hasRemaining();
        return bytes;
    }

    private int computeRawBytesSize() {
        int totalBytes = 0;
        for (int i = 0; i < length(); ++i) {
            Tag tag = tagAt(i);
            if (tag == Tag.INVALID) {
                continue; // always skip INVALID entries
            }
            totalBytes++; // tag
            totalBytes += switch (tag) {
                case INVALID -> 0;
                case UTF8 -> 2 + utf8At(i).length();
                case CLASS, STRING, MODULE, PACKAGE, METHODTYPE -> 2;
                case METHODHANDLE -> 3;
                case INTEGER, FLOAT, FIELD_REF, METHOD_REF, INTERFACE_METHOD_REF,
                                DYNAMIC, INVOKEDYNAMIC, NAME_AND_TYPE ->
                    4;
                case LONG, DOUBLE -> 8;
            };
        }
        return totalBytes;
    }

    public boolean immutableContentEquals(ConstantPool that) {
        return Arrays.equals(this.tags, that.tags) &&
                        Arrays.equals(this.entries, that.entries) &&
                        Arrays.equals(this.symbols, that.symbols);
    }
}
