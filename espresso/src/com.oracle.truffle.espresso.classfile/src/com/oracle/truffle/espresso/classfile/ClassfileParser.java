/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.MODULE;
import static com.oracle.truffle.espresso.classfile.ConstantPool.Tag.PACKAGE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ANNOTATION;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_DONT_INLINE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ENUM;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FORCE_INLINE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_HIDDEN;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_INTERFACE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_LAMBDA_FORM_COMPILED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_MODULE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PRIVATE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PROTECTED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PUBLIC;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SCOPED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_STABLE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_STATIC;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_STRICT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SUPER;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SYNCHRONIZED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SYNTHETIC;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VARARGS;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_VOLATILE;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_RECOGNIZED_CLASS_MODIFIERS;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.classfile.attributes.Attribute;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.ConstantValueAttribute;
import com.oracle.truffle.espresso.classfile.attributes.EnclosingMethodAttribute;
import com.oracle.truffle.espresso.classfile.attributes.ExceptionsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.InnerClassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.attributes.MethodParametersAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestHostAttribute;
import com.oracle.truffle.espresso.classfile.attributes.NestMembersAttribute;
import com.oracle.truffle.espresso.classfile.attributes.PermittedSubclassesAttribute;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceDebugExtensionAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceFileAttribute;
import com.oracle.truffle.espresso.classfile.attributes.StackMapTableAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ClassMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DoubleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FieldRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FloatConstant;
import com.oracle.truffle.espresso.classfile.constantpool.ImmutablePoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.IntegerConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InterfaceMethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvalidConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.LongConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.NameAndTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserSignatures;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserTypes;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.classfile.descriptors.ValidationException;
import com.oracle.truffle.espresso.classfile.perf.DebugCloseable;
import com.oracle.truffle.espresso.classfile.perf.DebugCounter;
import com.oracle.truffle.espresso.classfile.perf.DebugTimer;

@SuppressWarnings("try")
public final class ClassfileParser {

    private static final DebugTimer KLASS_PARSE = DebugTimer.create("klass parsing");
    private static final DebugTimer CONSTANT_POOL = DebugTimer.create("constant pool", KLASS_PARSE);
    private static final DebugTimer PARSE_INTERFACES = DebugTimer.create("interfaces", KLASS_PARSE);
    private static final DebugTimer PARSE_FIELD = DebugTimer.create("fields", KLASS_PARSE);
    private static final DebugTimer PARSE_CLASSATTR = DebugTimer.create("class attr", KLASS_PARSE);

    private static final DebugTimer PARSE_METHODS = DebugTimer.create("methods", KLASS_PARSE);
    private static final DebugTimer NO_DUP_CHECK = DebugTimer.create("method dup", PARSE_METHODS);
    private static final DebugTimer PARSE_SINGLE_METHOD = DebugTimer.create("single method", PARSE_METHODS);
    private static final DebugTimer METHOD_INIT = DebugTimer.create("method parse init", PARSE_SINGLE_METHOD);
    private static final DebugTimer NAME_CHECK = DebugTimer.create("name check", METHOD_INIT);
    private static final DebugTimer SIGNATURE_CHECK = DebugTimer.create("signature check", METHOD_INIT);

    private static final DebugTimer CODE_PARSE = DebugTimer.create("code parsing", PARSE_SINGLE_METHOD);
    private static final DebugTimer CODE_READ = DebugTimer.create("code read", CODE_PARSE);
    private static final DebugTimer EXCEPTION_HANDLERS = DebugTimer.create("exception handlers", CODE_PARSE);
    private static final DebugTimer VALIDATION = DebugTimer.create("CP validation", KLASS_PARSE);

    private static final DebugCounter UTF8_ENTRY_COUNT = DebugCounter.create("UTF8 Constant Pool entries");

    public static final int MAGIC = 0xCAFEBABE;

    public static final int JAVA_1_1_VERSION = 45;
    public static final int JAVA_1_2_VERSION = 46;
    public static final int JAVA_1_3_VERSION = 47;
    public static final int JAVA_1_4_VERSION = 48;
    public static final int JAVA_1_5_VERSION = 49;

    public static final int JAVA_6_VERSION = 50;
    public static final int JAVA_7_VERSION = 51;
    public static final int JAVA_8_VERSION = 52;
    public static final int JAVA_9_VERSION = 53;
    public static final int JAVA_10_VERSION = 54;
    public static final int JAVA_11_VERSION = 55;
    public static final int JAVA_12_VERSION = 56;
    public static final int JAVA_13_VERSION = 57;
    public static final int JAVA_14_VERSION = 58;
    public static final int JAVA_15_VERSION = 59;
    public static final int JAVA_16_VERSION = 60;
    public static final int JAVA_17_VERSION = 61;
    public static final int JAVA_18_VERSION = 62;
    public static final int JAVA_19_VERSION = 63;
    public static final int JAVA_20_VERSION = 64;
    public static final int JAVA_21_VERSION = 65;
    public static final int JAVA_22_VERSION = 66;
    public static final int JAVA_23_VERSION = 67;
    public static final int JAVA_24_VERSION = 68;
    public static final int JAVA_25_VERSION = 69;

    public static final int STRICTER_ACCESS_CTRL_CHECK_VERSION = JAVA_1_5_VERSION;
    public static final int STACKMAP_ATTRIBUTE_MAJOR_VERSION = JAVA_6_VERSION;
    public static final int INVOKEDYNAMIC_MAJOR_VERSION = JAVA_7_VERSION;
    public static final int NO_RELAX_ACCESS_CTRL_CHECK_VERSION = JAVA_8_VERSION;
    public static final int DYNAMICCONSTANT_MAJOR_VERSION = JAVA_11_VERSION;

    public static final char JAVA_MIN_SUPPORTED_VERSION = JAVA_1_1_VERSION;
    public static final char JAVA_MAX_SUPPORTED_MINOR_VERSION = 0;
    public static final char JAVA_PREVIEW_MINOR_VERSION = 65535;

    private final Symbol<Type> requestedClassType;

    private final ParsingContext parsingContext;

    private final ClassfileStream stream;

    private final boolean loaderIsBootOrPlatform;
    private final boolean isHidden;
    private final boolean forceAllowVMAnnotations;

    private Symbol<Type> classType;

    private int minorVersion = -1;
    private int majorVersion = -1;
    private int classFlags;

    private int maxBootstrapMethodAttrIndex = -1;
    private Tag badConstantSeen;

    private final boolean verifiable;

    private ImmutableConstantPool pool;
    private final boolean validate;

    private ClassfileParser(ParsingContext parsingContext, ClassfileStream stream, boolean verifiable, boolean loaderIsBootOrPlatform, Symbol<Type> requestedClassType, boolean isHidden,
                    boolean forceAllowVMAnnotations, boolean validate) {
        this.requestedClassType = requestedClassType;
        this.parsingContext = parsingContext;
        this.stream = Objects.requireNonNull(stream);
        this.verifiable = verifiable;
        this.loaderIsBootOrPlatform = loaderIsBootOrPlatform;
        this.isHidden = isHidden;
        this.forceAllowVMAnnotations = forceAllowVMAnnotations;
        this.validate = validate;
    }

    // Note: only used for reading the class name from class bytes
    private ClassfileParser(ParsingContext parsingContext, ClassfileStream stream) {
        this(parsingContext, stream, false, false, null, false, false, true);
    }

    void handleBadConstant(Tag tag, ClassfileStream s) {
        if (tag == Tag.MODULE || tag == Tag.PACKAGE) {
            if (majorVersion >= JAVA_9_VERSION) {
                s.readU2();
                badConstantSeen = tag;
            }
        } else {
            throw classFormatError("Unknown constant tag " + tag.getValue());
        }
    }

    private static ParserException classFormatError(String message) {
        throw new ParserException.ClassFormatError(message);
    }

    private static ParserException noClassDefFoundError(String message) {
        throw new ParserException.NoClassDefFoundError(message);
    }

    void updateMaxBootstrapMethodAttrIndex(int bsmAttrIndex) {
        if (maxBootstrapMethodAttrIndex < bsmAttrIndex) {
            maxBootstrapMethodAttrIndex = bsmAttrIndex;
        }
    }

    void checkInvokeDynamicSupport(Tag tag) {
        if (majorVersion < INVOKEDYNAMIC_MAJOR_VERSION) {
            throw classFormatError("Class file version does not support constant tag " + tag.getValue());
        }
    }

    void checkDynamicConstantSupport(Tag tag) {
        if (majorVersion < DYNAMICCONSTANT_MAJOR_VERSION) {
            throw classFormatError("Class file version does not support constant tag " + tag.getValue());
        }
    }

    public static ParserKlass parse(ParsingContext parsingContext, ClassfileStream stream, boolean verifiable, boolean loaderIsBootOrPlatform, Symbol<Type> requestedClassType, boolean isHidden,
                    boolean forceAllowVMAnnotations, boolean validate) throws ValidationException {
        return new ClassfileParser(parsingContext, stream, verifiable, loaderIsBootOrPlatform, requestedClassType, isHidden, forceAllowVMAnnotations, validate).parseClass();
    }

    private ParserKlass parseClass() throws ValidationException {
        try (DebugCloseable parse = KLASS_PARSE.scope(parsingContext.getTimers())) {
            return parseClassImpl();
        } catch (ParserException | ValidationException e) {
            throw e;
        } catch (Throwable e) {
            parsingContext.getLogger().log("Unexpected host exception thrown during class parsing", e);
            throw e;
        }
    }

    private void readMagic() {
        int magic = stream.readS4();
        if (magic != MAGIC) {
            throw classFormatError("Invalid magic number 0x" + Integer.toHexString(magic));
        }
    }

    /**
     * Verifies that the class file version is supported.
     *
     * @param major the major version number
     * @param minor the minor version number
     */
    private void verifyVersion(int major, int minor) {
        if (parsingContext.getJavaVersion().java8OrEarlier()) {
            versionCheck8OrEarlier(parsingContext.getJavaVersion().classFileVersion(), major, minor);
        } else if (parsingContext.getJavaVersion().java11OrEarlier()) {
            versionCheck11OrEarlier(parsingContext.getJavaVersion().classFileVersion(), major, minor);
        } else {
            versionCheck12OrLater(parsingContext.getJavaVersion().classFileVersion(), major, minor, parsingContext.isPreviewEnabled());
        }
    }

    /**
     * HotSpot rules (8): A legal major_version.minor_version must be one of the following:
     *
     * <li>Major_version >= 45 and major_version <= current_major_version, any minor version.
     * <li>Major_version = current_major_version and minor_version <= MAX_SUPPORTED_MINOR (= 0).
     */
    private static void versionCheck8OrEarlier(int maxMajor, int major, int minor) {
        if (major == maxMajor && minor <= JAVA_MAX_SUPPORTED_MINOR_VERSION) {
            return;
        }
        if (major >= JAVA_MIN_SUPPORTED_VERSION && major < maxMajor) {
            return;
        }
        throw unsupportedClassVersionError("Unsupported major.minor version " + major + "." + minor);
    }

    /**
     * HotSpot comment (11): A legal major_version.minor_version must be one of the following:
     *
     * <li>Major_version = 45, any minor_version.
     * <li>Major_version >= 46 and major_version <= current_major_version and minor_version = 0.
     * <li>Major_version = current_major_version and minor_version = 65535 and --enable-preview is
     * present.
     */
    private static void versionCheck11OrEarlier(int maxMajor, int major, int minor) {
        if (major == maxMajor && (minor <= JAVA_MAX_SUPPORTED_MINOR_VERSION || minor == JAVA_PREVIEW_MINOR_VERSION)) {
            return;
        }
        if (major < maxMajor && major > JAVA_MIN_SUPPORTED_VERSION && minor == 0) {
            return;
        }
        if (major == JAVA_MIN_SUPPORTED_VERSION) {
            return;
        }
        throw unsupportedClassVersionError("Unsupported major.minor version " + major + "." + minor);
    }

    /**
     * Hotspot comment (17): A legal major_version.minor_version must be one of the following:
     *
     * <li>Major_version >= 45 and major_version < 56, any minor_version.
     * <li>Major_version >= 56 and major_version <= JVM_CLASSFILE_MAJOR_VERSION and minor_version =
     * 0.
     * <li>Major_version = JVM_CLASSFILE_MAJOR_VERSION and minor_version = 65535 and
     * --enable-preview is present.
     */
    private static void versionCheck12OrLater(int maxMajor, int major, int minor, boolean previewEnabled) {
        if (major >= JAVA_12_VERSION && major <= maxMajor && minor == 0) {
            return;
        }
        if (major >= JAVA_MIN_SUPPORTED_VERSION && major < JAVA_12_VERSION) {
            return;
        }
        if (major == maxMajor && minor == JAVA_PREVIEW_MINOR_VERSION && previewEnabled) {
            return;
        }
        throw unsupportedClassVersionError("Unsupported major.minor version " + major + "." + minor);
    }

    private static ParserException unsupportedClassVersionError(String message) {
        return new ParserException.UnsupportedClassVersionError(message);
    }

    /**
     * Verifies that the flags for a class are valid.
     *
     * @param flags the flags to test
     * @throws ParserException.ClassFormatError if the flags are invalid
     */
    private static void verifyClassFlags(int flags, int majorVersion) {
        final boolean isInterface = (flags & ACC_INTERFACE) != 0;
        final boolean isAbstract = (flags & ACC_ABSTRACT) != 0;
        final boolean isFinal = (flags & ACC_FINAL) != 0;
        final boolean isSuper = (flags & ACC_SUPER) != 0;
        final boolean isEnum = (flags & ACC_ENUM) != 0;
        final boolean isAnnotation = (flags & ACC_ANNOTATION) != 0;
        final boolean majorGte15 = majorVersion >= JAVA_1_5_VERSION;

        if ((isAbstract && isFinal) ||
                        (isInterface && !isAbstract) ||
                        (isInterface && majorGte15 && (isSuper || isEnum)) ||
                        (!isInterface && majorGte15 && isAnnotation)) {
            throw classFormatError("Invalid class flags 0x" + Integer.toHexString(flags));
        }
    }

    boolean hasSeenBadConstant() {
        return badConstantSeen != null;
    }

    /**
     * Parses a constant pool from a class file.
     */
    private ImmutableConstantPool parseConstantPool() throws ValidationException {
        final int length = stream.readU2();
        if (length < 1) {
            throw classFormatError("Invalid constant pool size (" + length + ")");
        }
        int rawPoolStartPosition = stream.getPosition();
        ImmutablePoolConstant[] entries = new ImmutablePoolConstant[length];
        entries[0] = InvalidConstant.VALUE;

        int i = 1;
        while (i < length) {
            final int tagByte = stream.readU1();
            final Tag tag = Tag.fromValue(tagByte);
            if (tag == null) {
                throw ValidationException.raise("Invalid constant pool entry type at index " + i);
            }
            // check version for the tag except for module/package entries which must cause NDCFE
            if (!tag.isValidForVersion(getMajorVersion()) && tag != MODULE && tag != PACKAGE) {
                throw ValidationException.raise("Class file version does not support constant tag " + tagByte + " in class file");
            }
            switch (tag) {
                case CLASS: {
                    int classNameIndex = stream.readU2();
                    entries[i] = ClassConstant.create(classNameIndex);
                    break;
                }
                case STRING: {
                    int index = stream.readU2();
                    if (index == 0 || index >= length) {
                        throw ValidationException.raise("Invalid String constant index " + (i - 1));
                    }
                    entries[i] = StringConstant.create(index);
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
                    entries[i] = IntegerConstant.create(stream.readS4());
                    break;
                }
                case FLOAT: {
                    entries[i] = FloatConstant.create(stream.readFloat());
                    break;
                }
                case LONG: {
                    entries[i] = LongConstant.create(stream.readS8());
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw ValidationException.raise("Invalid long constant index " + (i - 1));
                    }
                    break;
                }
                case DOUBLE: {
                    entries[i] = DoubleConstant.create(stream.readDouble());
                    ++i;
                    try {
                        entries[i] = InvalidConstant.VALUE;
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw ValidationException.raise("Invalid double constant index " + (i - 1));
                    }
                    break;
                }
                case UTF8: {
                    // Copy-less UTF8 constant.
                    // A new symbol is spawned (copy) only if doesn't already exists.
                    UTF8_ENTRY_COUNT.inc();
                    ByteSequence bytes = stream.readByteSequenceUTF();
                    entries[i] = parsingContext.getOrCreateUtf8Constant(bytes);
                    break;
                }
                case METHODHANDLE: {
                    checkInvokeDynamicSupport(tag);
                    int refKind = stream.readU1();
                    int refIndex = stream.readU2();
                    entries[i] = MethodHandleConstant.create(refKind, refIndex);
                    break;
                }
                case METHODTYPE: {
                    checkInvokeDynamicSupport(tag);
                    entries[i] = MethodTypeConstant.create(stream.readU2());
                    break;
                }
                case DYNAMIC: {
                    checkDynamicConstantSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = DynamicConstant.create(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                case INVOKEDYNAMIC: {
                    checkInvokeDynamicSupport(tag);
                    int bootstrapMethodAttrIndex = stream.readU2();
                    int nameAndTypeIndex = stream.readU2();
                    entries[i] = InvokeDynamicConstant.create(bootstrapMethodAttrIndex, nameAndTypeIndex);
                    updateMaxBootstrapMethodAttrIndex(bootstrapMethodAttrIndex);
                    break;
                }
                default: {
                    handleBadConstant(tag, stream);
                    break;
                }
            }
            i++;
        }
        int rawPoolLength = stream.getPosition() - rawPoolStartPosition;

        ImmutableConstantPool constantPool = new ImmutableConstantPool(entries, majorVersion, minorVersion, rawPoolLength);

        if (hasSeenBadConstant()) {
            return constantPool;
        }

        // Cannot faithfully reconstruct patched pools. obtaining raw pool of patched/hidden class
        // is meaningless anyway.
        assert sameRawPool(constantPool, stream, rawPoolStartPosition, rawPoolLength);

        // Validation
        if (validate) {
            try (DebugCloseable handlers = VALIDATION.scope(parsingContext.getTimers())) {
                for (int j = 1; j < constantPool.length(); ++j) {
                    entries[j].validate(constantPool);
                }
            }
        }

        return constantPool;
    }

    private static boolean sameRawPool(ConstantPool constantPool, ClassfileStream stream, int rawPoolStartPosition, int rawPoolLength) {
        return Arrays.equals(constantPool.getRawBytes(), stream.getByteRange(rawPoolStartPosition, rawPoolLength));
    }

    private ParserKlass parseClassImpl() throws ValidationException {
        readMagic();

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        verifyVersion(majorVersion, minorVersion);

        try (DebugCloseable closeable = CONSTANT_POOL.scope(parsingContext.getTimers())) {
            this.pool = parseConstantPool();
        }

        // JVM_ACC_MODULE is defined in JDK-9 and later.
        if (majorVersion >= JAVA_9_VERSION) {
            classFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS | ACC_MODULE);
        } else {
            classFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS);
        }

        if ((classFlags & ACC_INTERFACE) != 0 && majorVersion < JAVA_6_VERSION) {
            // Set abstract bit for old class files for backward compatibility
            classFlags |= ACC_ABSTRACT;
        }

        boolean isModule = (classFlags & ACC_MODULE) != 0;
        if (isModule) {
            throw noClassDefFoundError(classType + " is not a class because access_flag ACC_MODULE is set");
        }

        if (hasSeenBadConstant()) {
            // Do not throw CFE until after the access_flags are checked because if
            // ACC_MODULE is set in the access flags, then NCDFE must be thrown, not CFE.
            // https://bugs.openjdk.java.net/browse/JDK-8175383
            throw stream.classFormatError("Unknown constant tag " + badConstantSeen);
        }

        verifyClassFlags(classFlags, majorVersion);

        // this class
        int thisKlassIndex = stream.readU2();

        Symbol<Name> thisKlassName = pool.classAt(thisKlassIndex).getName(pool);

        final boolean isInterface = Modifier.isInterface(classFlags);

        // TODO(peterssen): Verify class names.

        Symbol<Type> thisKlassType = parsingContext.getOrCreateTypeFromName(thisKlassName);
        if (TypeSymbols.isPrimitive(thisKlassType) || TypeSymbols.isArray(thisKlassType)) {
            throw classFormatError(".this_class cannot be array nor primitive " + classType);
        }

        // Update classType which could be null previously to reflect the name in the constant pool.
        classType = thisKlassType;

        // Checks if name in class file matches requested name
        if (requestedClassType != null && !isHidden && !requestedClassType.equals(classType)) {
            throw noClassDefFoundError(classType + " (wrong name: " + requestedClassType + ")");
        }

        Symbol<Type> superKlass = parseSuperKlass();

        if (!ParserTypes.java_lang_Object.equals(classType) && superKlass == null) {
            throw classFormatError("Class " + classType + " must have a superclass");
        }

        if (isInterface && !ParserTypes.java_lang_Object.equals(superKlass)) {
            throw classFormatError("Interface " + classType + " must extend java.lang.Object");
        }

        Symbol<Type>[] superInterfaces;
        try (DebugCloseable closeable = PARSE_INTERFACES.scope(parsingContext.getTimers())) {
            superInterfaces = parseInterfaces();
        }
        final ParserField[] fields;
        try (DebugCloseable closeable = PARSE_FIELD.scope(parsingContext.getTimers())) {
            fields = parseFields(isInterface);
        }
        final ParserMethod[] methods;
        try (DebugCloseable closeable = PARSE_METHODS.scope(parsingContext.getTimers())) {
            methods = parseMethods(isInterface);
        }
        final Attribute[] attributes;
        try (DebugCloseable closeable = PARSE_CLASSATTR.scope(parsingContext.getTimers())) {
            attributes = parseClassAttributes();
        }

        // Ensure there are no trailing bytes
        stream.checkEndOfFile();

        return new ParserKlass(pool, classFlags, thisKlassName, thisKlassType, superKlass, superInterfaces, methods, fields, attributes, thisKlassIndex, -1);
    }

    public static Symbol<Name> getClassName(ParsingContext parsingContext, byte[] bytes) throws ValidationException {
        return new ClassfileParser(parsingContext, new ClassfileStream(bytes, null)).getClassName();
    }

    private Symbol<Name> getClassName() throws ValidationException {
        readMagic();
        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        verifyVersion(majorVersion, minorVersion);
        try (DebugCloseable closeable = CONSTANT_POOL.scope(parsingContext.getTimers())) {
            this.pool = parseConstantPool();
        }
        stream.readU2(); // access flags
        int thisKlassIndex = stream.readU2();
        return pool.classAt(thisKlassIndex).getName(pool);
    }

    private ParserMethod[] parseMethods(boolean isInterface) throws ValidationException {
        int methodCount = stream.readU2();
        if (methodCount == 0) {
            return ParserMethod.EMPTY_ARRAY;
        }
        ParserMethod[] methods = new ParserMethod[methodCount];
        /*
         * Classes in general have lots of methods: use a hash set rather than array lookup for dup
         * check.
         */
        final HashSet<MethodKey> dup = validate ? new HashSet<>(methodCount) : null;
        for (int i = 0; i < methodCount; ++i) {
            ParserMethod method;
            try (DebugCloseable closeable = PARSE_SINGLE_METHOD.scope(parsingContext.getTimers())) {
                method = parseMethod(isInterface);
            }
            methods[i] = method;
            if (validate) {
                try (DebugCloseable closeable = NO_DUP_CHECK.scope(parsingContext.getTimers())) {
                    if (!dup.add(new MethodKey(method))) {
                        throw classFormatError("Duplicate method name and signature: " + method.getName() + " " + method.getSignature());
                    }
                }
            }
        }

        return methods;
    }

    /**
     * Verifies that the flags for a method are valid.
     *
     * @param flags the flags to test
     * @param isInit true if the method is "<init>"
     * @param isClinit true if the method is "<clinit>"
     * @param majorVersion the version of the class file
     * @throws ParserException.ClassFormatError if the flags are invalid
     */
    private static void verifyMethodFlags(int flags, boolean isInterface, boolean isInit, boolean isClinit, int majorVersion) {
        // The checks below are based on JVMS8 4.6

        // Class and interface initialization methods are called
        // implicitly by the Java Virtual Machine. The value of their
        // access_flags item is ignored except for the setting of the
        // ACC_STRICT flag.
        if (isClinit) {
            return;
        }

        boolean valid = true;

        // If a method of a class or interface has its ACC_ABSTRACT flag set, it
        // must not have any of its ACC_PRIVATE, ACC_STATIC, ACC_FINAL,
        // ACC_SYNCHRONIZED, ACC_NATIVE, or ACC_STRICT flags set.
        if ((flags & ACC_ABSTRACT) != 0) {
            valid &= (flags & (ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_NATIVE)) == 0;
            if (majorVersion >= JAVA_1_5_VERSION) {
                valid &= (flags & (ACC_SYNCHRONIZED | ACC_STRICT)) == 0;
            }
        }

        if (valid) {
            if (!isInterface) {
                // Methods of classes may have any of the flags in Table 4.6-A
                // set. However, each method of a class may have at most one of
                // its ACC_PUBLIC, ACC_PRIVATE, and ACC_PROTECTED flags set

                // These are all small bits. The value is between 0 and 7.
                final int maskedFlags = flags & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED);
                valid &= maskedFlags == 0 || (maskedFlags & ~(maskedFlags - 1)) == maskedFlags;
            } else {
                // Methods of interfaces may have any of the flags in Table
                // 4.6-A set except ACC_PROTECTED, ACC_FINAL, ACC_SYNCHRONIZED,
                // and ACC_NATIVE
                valid &= (flags & (ACC_PROTECTED | ACC_FINAL | ACC_SYNCHRONIZED | ACC_NATIVE)) == 0;

                // In a class file whose version number is less than 52.0, each
                // method of an interface must have its ACC_PUBLIC and
                // ACC_ABSTRACT flags set; in a class file whose version number
                // is 52.0 or above, each method of an interface must have
                // exactly one of its ACC_PUBLIC and ACC_PRIVATE flags set.
                if (majorVersion < JAVA_8_VERSION) {
                    final int publicAndAbstract = ACC_PUBLIC | ACC_ABSTRACT;
                    valid &= (flags & publicAndAbstract) == publicAndAbstract;
                    // And since it is public it can't be private as well
                    valid &= (flags & ACC_PRIVATE) == 0;
                } else {
                    final int maskedFlags = flags & (ACC_PUBLIC | ACC_PRIVATE);
                    valid &= (maskedFlags != 0) & (maskedFlags & ~(maskedFlags - 1)) == maskedFlags;
                }
            }

            if (valid) {
                if (isInit) {
                    // Each instance initialization method may have at most one of
                    // its ACC_PUBLIC, ACC_PRIVATE, and ACC_PROTECTED flags set
                    // (this part is already checked above), and may also have its
                    // ACC_VARARGS, ACC_STRICT, and ACC_SYNTHETIC flags set, but
                    // must not have any of the other flags in Table 4.6-A set.
                    valid &= (flags & JVM_RECOGNIZED_METHOD_MODIFIERS & ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_VARARGS | ACC_STRICT | ACC_SYNTHETIC)) == 0;
                }
            }
        }
        if (!valid) {
            throw classFormatError("Invalid method flags 0x" + Integer.toHexString(flags));
        }
    }

    // @formatter:off
    private static final int RUNTIME_VISIBLE_ANNOTATIONS =             0b00000001;
    private static final int RUNTIME_INVISIBLE_ANNOTATIONS =           0b00000010;
    private static final int RUNTIME_VISIBLE_TYPE_ANNOTATIONS =        0b00000100;
    private static final int RUNTIME_INVISIBLE_TYPE_ANNOTATIONS =      0b00001000;
    private static final int RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS =   0b00010000;
    private static final int RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS = 0b00100000;
    private static final int ANNOTATION_DEFAULT =                      0b01000000;
    private static final int SIGNATURE =                               0b10000000;
    // @formatter:on

    private static final int classAnnotations = RUNTIME_VISIBLE_ANNOTATIONS | RUNTIME_INVISIBLE_ANNOTATIONS | RUNTIME_VISIBLE_TYPE_ANNOTATIONS | RUNTIME_INVISIBLE_TYPE_ANNOTATIONS | SIGNATURE;
    private static final int methodAnnotations = RUNTIME_VISIBLE_ANNOTATIONS | RUNTIME_INVISIBLE_ANNOTATIONS | RUNTIME_VISIBLE_PARAMETER_ANNOTATIONS | RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS |
                    RUNTIME_VISIBLE_TYPE_ANNOTATIONS | RUNTIME_INVISIBLE_TYPE_ANNOTATIONS | ANNOTATION_DEFAULT | SIGNATURE;
    private static final int fieldAnnotations = RUNTIME_VISIBLE_ANNOTATIONS | RUNTIME_INVISIBLE_ANNOTATIONS | RUNTIME_VISIBLE_TYPE_ANNOTATIONS | RUNTIME_INVISIBLE_TYPE_ANNOTATIONS | SIGNATURE;
    private static final int codeAnnotations = RUNTIME_VISIBLE_TYPE_ANNOTATIONS | RUNTIME_INVISIBLE_TYPE_ANNOTATIONS | SIGNATURE;
    private static final int recordAnnotations = RUNTIME_VISIBLE_ANNOTATIONS | RUNTIME_INVISIBLE_ANNOTATIONS |
                    RUNTIME_VISIBLE_TYPE_ANNOTATIONS | RUNTIME_INVISIBLE_TYPE_ANNOTATIONS | SIGNATURE;

    public enum InfoType {
        Class(classAnnotations),
        Method(methodAnnotations),
        Field(fieldAnnotations),
        Code(codeAnnotations),
        Record(recordAnnotations);

        final int annotations;

        InfoType(int annotations) {
            this.annotations = annotations;
        }

        boolean supports(int annotation) {
            return (annotations & annotation) != 0;
        }
    }

    private enum AnnotationLocation {
        Method,
        Field,
        Class
    }

    private record RuntimeVisibleAnnotationsAttribute(Attribute attribute, int flags) {
        private RuntimeVisibleAnnotationsAttribute {
            Objects.requireNonNull(attribute);
        }
    }

    private class CommonAttributeParser {

        final InfoType infoType;

        CommonAttributeParser(InfoType infoType) {
            this.infoType = infoType;
        }

        Attribute runtimeVisibleAnnotations = null;
        Attribute runtimeInvisibleAnnotations = null;
        Attribute runtimeVisibleTypeAnnotations = null;
        Attribute runtimeInvisibleTypeAnnotations = null;
        Attribute runtimeVisibleParameterAnnotations = null;
        Attribute runtimeInvisibleParameterAnnotations = null;
        Attribute annotationDefault = null;
        Attribute signature = null;

        Attribute parseCommonAttribute(Symbol<Name> attributeName, int attributeSize) throws ValidationException {
            if (infoType.supports(RUNTIME_VISIBLE_ANNOTATIONS) && attributeName.equals(ParserNames.RuntimeVisibleAnnotations)) {
                if (runtimeVisibleAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeVisibleAnnotations attribute");
                }
                return runtimeVisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_VISIBLE_TYPE_ANNOTATIONS) && attributeName.equals(ParserNames.RuntimeVisibleTypeAnnotations)) {
                if (runtimeVisibleTypeAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                }
                return runtimeVisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_ANNOTATIONS) && attributeName.equals(ParserNames.RuntimeInvisibleAnnotations)) {
                if (runtimeInvisibleAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                }
                return runtimeInvisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_TYPE_ANNOTATIONS) && attributeName.equals(ParserNames.RuntimeInvisibleTypeAnnotations)) {
                if (runtimeInvisibleTypeAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeInvisibleTypeAnnotations attribute");
                }
                return runtimeInvisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS) && attributeName.equals(ParserNames.RuntimeVisibleParameterAnnotations)) {
                if (runtimeVisibleParameterAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeVisibleParameterAnnotations attribute");
                }
                return runtimeVisibleParameterAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS) && attributeName.equals(ParserNames.RuntimeInvisibleParameterAnnotations)) {
                if (runtimeInvisibleParameterAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeVisibleParameterAnnotations attribute");
                }
                return runtimeInvisibleParameterAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(ANNOTATION_DEFAULT) && attributeName.equals(ParserNames.AnnotationDefault)) {
                if (annotationDefault != null) {
                    throw classFormatError("Duplicate AnnotationDefault attribute");
                }
                return annotationDefault = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(SIGNATURE) && attributeName.equals(ParserNames.Signature)) {
                if (parsingContext.getJavaVersion().java9OrLater() && signature != null) {
                    throw classFormatError("Duplicate AnnotationDefault attribute");
                }
                if (attributeSize != 2) {
                    throw classFormatError("Invalid attribute_length value for signature attribute: " + attributeSize + " != 2");
                }
                return signature = parseSignatureAttribute(attributeName);
            }
            return null;
        }

        RuntimeVisibleAnnotationsAttribute parseRuntimeVisibleAnnotations(int attributeSize, AnnotationLocation location) {
            assert infoType.supports(RUNTIME_VISIBLE_ANNOTATIONS);
            if (runtimeVisibleAnnotations != null) {
                throw classFormatError("Duplicate RuntimeVisibleAnnotations attribute");
            }

            // Check for special internal annotations
            byte[] data = stream.readByteArray(attributeSize);
            ClassfileStream subStream = new ClassfileStream(data, stream.getClasspathFile());
            int flags = 0;
            if (loaderIsBootOrPlatform || forceAllowVMAnnotations) {
                flags = switch (location) {
                    case Method -> parseMethodVMAnnotations(subStream);
                    case Field -> parseFieldVMAnnotations(subStream);
                    case Class -> 0;
                };
            }

            Attribute attribute = new Attribute(ParserNames.RuntimeVisibleAnnotations, data);
            runtimeVisibleAnnotations = attribute;
            return new RuntimeVisibleAnnotationsAttribute(attribute, flags);
        }

        private int parseMethodVMAnnotations(ClassfileStream subStream) {
            int flags = 0;
            int count = subStream.readU2();
            for (int j = 0; j < count; j++) {
                int typeIndex = parseAnnotation(subStream);
                Utf8Constant constant = pool.utf8At(typeIndex, "annotation type");
                // Validation of the type is done at runtime by guest java code.
                Symbol<Type> annotType = constant.unsafeSymbolValue();
                if (ParserTypes.java_lang_invoke_LambdaForm$Compiled.equals(annotType)) {
                    flags |= ACC_LAMBDA_FORM_COMPILED;
                } else if (ParserTypes.java_lang_invoke_LambdaForm$Hidden.equals(annotType) ||
                                ParserTypes.jdk_internal_vm_annotation_Hidden.equals(annotType)) {
                    flags |= ACC_HIDDEN;
                } else if (ParserTypes.sun_reflect_CallerSensitive.equals(annotType) ||
                                ParserTypes.jdk_internal_reflect_CallerSensitive.equals(annotType)) {
                    flags |= ACC_CALLER_SENSITIVE;
                } else if (ParserTypes.java_lang_invoke_ForceInline.equals(annotType) ||
                                ParserTypes.jdk_internal_vm_annotation_ForceInline.equals(annotType)) {
                    flags |= ACC_FORCE_INLINE;
                } else if (ParserTypes.java_lang_invoke_DontInline.equals(annotType) ||
                                ParserTypes.jdk_internal_vm_annotation_DontInline.equals(annotType)) {
                    flags |= ACC_DONT_INLINE;
                } else if (ParserTypes.jdk_internal_misc_ScopedMemoryAccess$Scoped.equals(annotType)) {
                    flags |= ACC_SCOPED;
                }
            }
            return flags;
        }

        private int parseFieldVMAnnotations(ClassfileStream subStream) {
            int flags = 0;
            int count = subStream.readU2();
            for (int j = 0; j < count; j++) {
                int typeIndex = parseAnnotation(subStream);
                Utf8Constant constant = pool.utf8At(typeIndex, "annotation type");
                // Validation of the type is done at runtime by guest java code.
                Symbol<Type> annotType = constant.unsafeSymbolValue();
                if (ParserTypes.jdk_internal_vm_annotation_Stable.equals(annotType)) {
                    flags |= ACC_STABLE;
                }
            }
            return flags;
        }
    }

    private ParserMethod parseMethod(boolean isInterface) throws ValidationException {
        int methodFlags = stream.readU2();
        int nameIndex = stream.readU2();
        int signatureIndex = stream.readU2();
        final Symbol<Name> name;
        final Symbol<Signature> signature;
        int attributeCount;
        Attribute[] methodAttributes;

        int extraFlags = methodFlags;
        boolean isClinit = false;
        boolean isInit = false;

        try (DebugCloseable closeable = METHOD_INIT.scope(parsingContext.getTimers())) {

            try (DebugCloseable nameCheck = NAME_CHECK.scope(parsingContext.getTimers())) {
                name = validateMethodName(pool.utf8At(nameIndex, "method name"), true);

                if (name.equals(ParserNames._clinit_)) {
                    // Class and interface initialization methods (3.9) are called
                    // implicitly by the Java virtual machine; the value of their
                    // access_flags item is ignored except for the settings of the
                    // ACC_STRICT flag.
                    if (majorVersion < JAVA_7_VERSION) {
                        // Backwards compatibility.
                        methodFlags = ACC_STATIC;
                    } else if ((methodFlags & ACC_STATIC) == ACC_STATIC) {
                        methodFlags &= (ACC_STRICT | ACC_STATIC);
                    } else if (parsingContext.getJavaVersion().java9OrLater()) {
                        throw classFormatError("Method <clinit> is not static.");
                    }
                    // extraFlags = INITIALIZER | methodFlags;
                    isClinit = true;
                } else if (name.equals(ParserNames._init_)) {
                    if (isInterface) {
                        throw classFormatError("Method <init> is not valid in an interface.");
                    }
                    isInit = true;
                }
            }

            final boolean isStatic = Modifier.isStatic(extraFlags);

            verifyMethodFlags(methodFlags, isInterface, isInit, isClinit, majorVersion);

            /*
             * A method is a class or interface initialization method if all of the following are
             * true:
             *
             * It has the special name <clinit>.
             *
             * It is void (4.3.3). (checked earlier)
             *
             * In a class file whose version number is 51.0 or above, the method has its ACC_STATIC
             * flag set and takes no arguments (4.6).
             */
            try (DebugCloseable signatureCheck = SIGNATURE_CHECK.scope(parsingContext.getTimers())) {
                // Checks for void method if init or clinit.
                /*
                 * Obtain slot number for the signature. Forces a validation, but better in startup
                 * than going twice through the sequence, once for validation, once for slots.
                 */
                int slots = pool.utf8At(signatureIndex).validateSignatureGetSlots(isInit || isClinit);

                assert Validation.validSignatureDescriptor(pool.symbolAtUnsafe(signatureIndex));
                signature = pool.symbolAtUnsafe(signatureIndex, "method descriptor");

                if (isClinit && majorVersion >= JAVA_7_VERSION) {
                    // Checks clinit takes no arguments.
                    if (!signature.equals(ParserSignatures._void)) {
                        throw classFormatError("Method <clinit> has invalid signature: " + signature);
                    }
                }

                if (slots + (isStatic ? 0 : 1) > 255) {
                    throw classFormatError("Too many arguments in method signature: " + signature);
                }

                if (ParserNames.finalize.equals(name) && ParserSignatures._void.equals(signature) && !Modifier.isStatic(methodFlags) && !ParserTypes.java_lang_Object.equals(classType)) {
                    // This class has a finalizer method implementation (ignore for
                    // java.lang.Object).
                    classFlags |= ACC_FINALIZER;
                }
            }
            attributeCount = stream.readU2();
            methodAttributes = spawnAttributesArray(attributeCount);
        }

        CodeAttribute codeAttribute = null;
        Attribute checkedExceptions = null;

        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Method);

        MethodParametersAttribute methodParameters = null;

        for (int i = 0; i < attributeCount; ++i) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAtUnsafe(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (attributeName.equals(ParserNames.Code)) {
                if (codeAttribute != null) {
                    throw classFormatError("Duplicate Code attribute");
                }
                try (DebugCloseable code = CODE_PARSE.scope(parsingContext.getTimers())) {
                    methodAttributes[i] = codeAttribute = parseCodeAttribute(attributeName);
                }
            } else if (attributeName.equals(ParserNames.Exceptions)) {
                if (checkedExceptions != null) {
                    throw classFormatError("Duplicate Exceptions attribute");
                }
                methodAttributes[i] = checkedExceptions = parseExceptions(attributeName);
            } else if (attributeName.equals(ParserNames.Synthetic)) {
                methodFlags |= ACC_SYNTHETIC;
                methodAttributes[i] = checkedExceptions = new Attribute(attributeName, null);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (attributeName.equals(ParserNames.RuntimeVisibleAnnotations)) {
                    RuntimeVisibleAnnotationsAttribute annotations = commonAttributeParser.parseRuntimeVisibleAnnotations(attributeSize, AnnotationLocation.Method);
                    methodFlags |= annotations.flags;
                    methodAttributes[i] = annotations.attribute;
                } else if (attributeName.equals(ParserNames.MethodParameters)) {
                    if (methodParameters != null) {
                        throw classFormatError("Duplicate MethodParameters attribute");
                    }
                    methodAttributes[i] = methodParameters = parseMethodParameters(attributeName);
                } else {
                    Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                    methodAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
                }
            } else {
                methodAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            final int distance = stream.getPosition() - startPosition;
            if (attributeSize != distance) {
                final String message = "Invalid attribute_length for " + attributeName + " attribute (reported " + attributeSize + " != parsed " + distance + ")";
                throw classFormatError(message);
            }
        }

        if (Modifier.isAbstract(methodFlags) || Modifier.isNative(methodFlags)) {
            if (codeAttribute != null) {
                throw classFormatError("Code attribute supplied for native or abstract method");
            }
        } else {
            if (codeAttribute == null) {
                throw classFormatError("Missing Code attribute");
            }
        }

        if (isHidden) {
            methodFlags |= ACC_HIDDEN;
        }

        return ParserMethod.create(methodFlags, name, signature, methodAttributes);
    }

    private static Attribute[] spawnAttributesArray(int attributeCount) {
        return attributeCount == 0 ? Attribute.EMPTY_ARRAY : new Attribute[attributeCount];
    }

    private static int parseAnnotation(ClassfileStream subStream) {
        int typeIndex = subStream.readU2();
        int numElementValuePairs = subStream.readU2();
        for (int k = 0; k < numElementValuePairs; k++) {
            /* elementNameIndex */
            subStream.readU2();
            skipElementValue(subStream);
        }
        return typeIndex;
    }

    private static void skipElementValue(ClassfileStream subStream) {
        char tag = (char) subStream.readU1();
        switch (tag) {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 's':
                /* constValueIndex */
                subStream.readU2();
                break;
            case 'e':
                /* typeNameIndex */
                subStream.readU2();
                /* constNameIndex */
                subStream.readU2();
                break;
            case 'c':
                /* classInfoIndex */
                subStream.readU2();
                break;
            case '@':
                /* ignore */
                parseAnnotation(subStream);
                break;
            case '[':
                int numValues = subStream.readU2();
                for (int i = 0; i < numValues; i++) {
                    skipElementValue(subStream);
                }
                break;
            default:
                throw classFormatError("Invalid annotation tag: " + tag);
        }

    }

    private Attribute[] parseClassAttributes() throws ValidationException {
        int attributeCount = stream.readU2();
        if (attributeCount == 0) {
            if (maxBootstrapMethodAttrIndex >= 0) {
                throw classFormatError("BootstrapMethods attribute is missing");
            }
            return Attribute.EMPTY_ARRAY;
        }

        SourceFileAttribute sourceFileName = null;
        SourceDebugExtensionAttribute sourceDebugExtensionAttribute = null;
        NestHostAttribute nestHost = null;
        NestMembersAttribute nestMembers = null;
        EnclosingMethodAttribute enclosingMethod = null;
        BootstrapMethodsAttribute bootstrapMethods = null;
        InnerClassesAttribute innerClasses = null;
        PermittedSubclassesAttribute permittedSubclasses = null;
        RecordAttribute record = null;

        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Class);

        final Attribute[] classAttributes = spawnAttributesArray(attributeCount);
        for (int i = 0; i < attributeCount; i++) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAtUnsafe(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (attributeName.equals(ParserNames.SourceFile)) {
                if (sourceFileName != null) {
                    throw classFormatError("Duplicate SourceFile attribute");
                }
                classAttributes[i] = sourceFileName = parseSourceFileAttribute(attributeName);
            } else if (attributeName.equals(ParserNames.SourceDebugExtension)) {
                if (sourceDebugExtensionAttribute != null) {
                    throw classFormatError("Duplicate SourceDebugExtension attribute");
                }
                classAttributes[i] = sourceDebugExtensionAttribute = parseSourceDebugExtensionAttribute(attributeName, attributeSize);
            } else if (attributeName.equals(ParserNames.Synthetic)) {
                classFlags |= ACC_SYNTHETIC;
                classAttributes[i] = new Attribute(attributeName, null);
            } else if (attributeName.equals(ParserNames.InnerClasses)) {
                if (innerClasses != null) {
                    throw classFormatError("Duplicate InnerClasses attribute");
                }
                classAttributes[i] = innerClasses = parseInnerClasses(attributeName);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (majorVersion >= JAVA_7_VERSION && attributeName.equals(ParserNames.BootstrapMethods)) {
                    if (bootstrapMethods != null) {
                        throw classFormatError("Duplicate BootstrapMethods attribute");
                    }
                    classAttributes[i] = bootstrapMethods = parseBootstrapMethods(attributeName);
                } else if (attributeName.equals(ParserNames.EnclosingMethod)) {
                    if (enclosingMethod != null) {
                        throw classFormatError("Duplicate EnclosingMethod attribute");
                    }
                    classAttributes[i] = enclosingMethod = parseEnclosingMethodAttribute(attributeName);
                } else if (majorVersion >= JAVA_11_VERSION && attributeName.equals(ParserNames.NestHost)) {
                    if (nestHost != null) {
                        throw classFormatError("Duplicate NestHost attribute");
                    }
                    if (nestMembers != null) {
                        throw classFormatError("Classfile cannot have both a nest members and a nest host attribute.");
                    }
                    if (attributeSize != 2) {
                        throw classFormatError("Attribute length of NestHost must be 2");
                    }
                    classAttributes[i] = nestHost = parseNestHostAttribute(attributeName);
                } else if (majorVersion >= JAVA_11_VERSION && attributeName.equals(ParserNames.NestMembers)) {
                    if (nestMembers != null) {
                        throw classFormatError("Duplicate NestMembers attribute");
                    }
                    if (nestHost != null) {
                        throw classFormatError("Classfile cannot have both a nest members and a nest host attribute.");
                    }
                    classAttributes[i] = nestMembers = parseNestMembers(attributeName);
                } else if (majorVersion >= JAVA_14_VERSION && attributeName.equals(ParserNames.Record)) {
                    if (record != null) {
                        throw classFormatError("Duplicate Record attribute");
                    }
                    classAttributes[i] = record = parseRecord(attributeName);
                } else if (majorVersion >= JAVA_17_VERSION && attributeName.equals(ParserNames.PermittedSubclasses)) {
                    if (permittedSubclasses != null) {
                        throw classFormatError("Duplicate PermittedSubclasses attribute");
                    }
                    classAttributes[i] = permittedSubclasses = parsePermittedSubclasses(attributeName);
                } else {
                    Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                    // stream.skip(attributeSize);
                    classAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
                }
            } else {
                // stream.skip(attributeSize);
                classAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw classFormatError("Invalid attribute length for " + attributeName + " attribute");
            }
        }

        if (maxBootstrapMethodAttrIndex >= 0 && bootstrapMethods == null) {
            throw classFormatError("BootstrapMethods attribute is missing");
        }

        return classAttributes;
    }

    private SourceFileAttribute parseSourceFileAttribute(Symbol<Name> name) {
        assert ParserNames.SourceFile.equals(name);
        int sourceFileIndex = stream.readU2();
        return new SourceFileAttribute(name, sourceFileIndex);
    }

    private SourceDebugExtensionAttribute parseSourceDebugExtensionAttribute(Symbol<Name> name, int attributeLength) {
        assert ParserNames.SourceDebugExtension.equals(name);
        byte[] debugExBytes = stream.readByteArray(attributeLength);

        String debugExtension = null;
        try {
            debugExtension = ModifiedUTF8.toJavaString(debugExBytes);
        } catch (IOException e) {
            // not able to convert the debug extension bytes to Java String
            // this isn't critical, so we'll just mark as null
        }
        return new SourceDebugExtensionAttribute(name, debugExtension);
    }

    private LineNumberTableAttribute parseLineNumberTable(Symbol<Name> name) {
        assert ParserNames.LineNumberTable.equals(name);
        int entryCount = stream.readU2();
        if (entryCount == 0) {
            return LineNumberTableAttribute.EMPTY;
        }
        char[] entries = new char[entryCount << 1];
        for (int i = 0; i < entryCount; i++) {
            int idx = i << 1;
            char bci = (char) stream.readU2();
            char lineNumber = (char) stream.readU2();
            entries[idx] = bci;
            entries[idx + 1] = lineNumber;
        }
        return new LineNumberTableAttribute(name, entries);
    }

    private LocalVariableTable parseLocalVariableAttribute(Symbol<Name> name, int codeLength, int maxLocals) throws ValidationException {
        assert ParserNames.LocalVariableTable.equals(name);
        return parseLocalVariableTable(name, codeLength, maxLocals);
    }

    private LocalVariableTable parseLocalVariableTypeAttribute(Symbol<Name> name, int codeLength, int maxLocals) throws ValidationException {
        assert ParserNames.LocalVariableTypeTable.equals(name);
        return parseLocalVariableTable(name, codeLength, maxLocals);
    }

    private LocalVariableTable parseLocalVariableTable(Symbol<Name> name, int codeLength, int maxLocals) throws ValidationException {
        boolean isLVTT = ParserNames.LocalVariableTypeTable.equals(name);
        int entryCount = stream.readU2();
        if (entryCount == 0) {
            return isLVTT ? LocalVariableTable.EMPTY_LVTT : LocalVariableTable.EMPTY_LVT;
        }
        Local[] locals = new Local[entryCount];
        for (int i = 0; i < entryCount; i++) {
            int bci = stream.readU2();
            int length = stream.readU2();
            int nameIndex = stream.readU2();
            int descIndex = stream.readU2();
            int slot = stream.readU2();

            if (bci < 0 || bci >= codeLength) {
                throw classFormatError("Invalid local variable table attribute entry: start_pc out of bounds: " + bci);
            }
            if (bci + length > codeLength) {
                throw classFormatError("Invalid local variable table attribute entry: start_pc + length out of bounds: " + (bci + length));
            }

            Utf8Constant poolName = pool.utf8At(nameIndex);
            Utf8Constant typeNameOrSignature = pool.utf8At(descIndex);

            int extraSlot = 0;
            if (!isLVTT) {
                Symbol<Type> type = validateType(typeNameOrSignature, false);
                if (type == ParserTypes._long || type == ParserTypes._double) {
                    extraSlot = 1;
                }
            }

            if (slot + extraSlot >= maxLocals) {
                throw classFormatError("Invalid local variable table attribute entry: index points to an invalid frame slot: " + slot);
            }

            locals[i] = new Local(
                            validateFieldName(poolName),
                            isLVTT ? null : validateType(typeNameOrSignature, false),
                            isLVTT ? typeNameOrSignature.unsafeSymbolValue() : null,
                            bci, bci + length - 1, slot);

        }
        return new LocalVariableTable(name, locals);
    }

    private SignatureAttribute parseSignatureAttribute(Symbol<Name> name) throws ValidationException {
        assert ParserNames.Signature.equals(name);
        int signatureIndex = stream.readU2();
        validateEncoding(pool.utf8At(signatureIndex, "signature attribute"));
        return new SignatureAttribute(name, signatureIndex);
    }

    private MethodParametersAttribute parseMethodParameters(Symbol<Name> name) {
        int entryCount = stream.readU1();
        if (entryCount == 0) {
            return MethodParametersAttribute.EMPTY;
        }
        MethodParametersAttribute.Entry[] entries = new MethodParametersAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; i++) {
            int nameIndex = stream.readU2();
            int accessFlags = stream.readU2();
            entries[i] = new MethodParametersAttribute.Entry(nameIndex, accessFlags);
        }
        return new MethodParametersAttribute(name, entries);
    }

    private ExceptionsAttribute parseExceptions(Symbol<Name> name) {
        assert ParserNames.Exceptions.equals(name);
        int entryCount = stream.readU2();
        int[] entries = new int[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int index = stream.readU2();
            if (index >= pool.length()) {
                throw classFormatError("Invalid exception_index_table: out of bounds.");
            }
            if (index == 0) {
                throw classFormatError("Invalid exception_index_table: 0.");
            }
            if (pool.tagAt(index) != Tag.CLASS) {
                throw classFormatError("Invalid exception_index_table: not a CONSTANT_Class_info structure.");
            }
            entries[i] = index;
        }
        return new ExceptionsAttribute(name, entries);
    }

    private BootstrapMethodsAttribute parseBootstrapMethods(Symbol<Name> name) {
        int entryCount = stream.readU2();
        if (maxBootstrapMethodAttrIndex >= entryCount) {
            throw classFormatError("Invalid bootstrapMethod index: " + maxBootstrapMethodAttrIndex + ", actual bootstrap methods size: " + entryCount);
        }
        BootstrapMethodsAttribute.Entry[] entries = new BootstrapMethodsAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int bootstrapMethodRef = stream.readU2();
            if (bootstrapMethodRef == 0) {
                throw classFormatError("Invalid bootstrapMethodRefIndex: 0");
            }
            if (bootstrapMethodRef >= pool.length()) {
                throw classFormatError("Invalid bootstrapMethodRefIndex: out of bounds.");
            }
            if (pool.tagAt(bootstrapMethodRef) != Tag.METHODHANDLE) {
                throw classFormatError("Invalid bootstrapMethodRefIndex: not a CONSTANT_MethodHandle_info structure.");
            }
            if (maxBootstrapMethodAttrIndex >= entryCount) {
                throw classFormatError("bootstrap_method_attr_index is greater than maximum valid index in the BootstrapMethods attribute.");
            }
            int numBootstrapArguments = stream.readU2();
            char[] bootstrapArguments = new char[numBootstrapArguments];
            for (int j = 0; j < numBootstrapArguments; ++j) {
                char cpIndex = (char) stream.readU2();
                if (!pool.tagAt(cpIndex).isLoadable()) {
                    throw classFormatError("Invalid constant pool constant for BootstrapMethodAttribute. Not a loadable constant");
                }
                bootstrapArguments[j] = cpIndex;
            }
            entries[i] = new BootstrapMethodsAttribute.Entry((char) bootstrapMethodRef, bootstrapArguments);
        }
        return new BootstrapMethodsAttribute(name, entries);
    }

    private InnerClassesAttribute parseInnerClasses(Symbol<Name> name) throws ValidationException {
        assert ParserNames.InnerClasses.equals(name);
        final int entryCount = stream.readU2();

        boolean duplicateInnerClass = false;

        final InnerClassesAttribute.Entry[] innerClassInfos = new InnerClassesAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            final InnerClassesAttribute.Entry innerClassInfo = parseInnerClassEntry();
            final int innerClassIndex = innerClassInfo.innerClassIndex;
            final int outerClassIndex = innerClassInfo.outerClassIndex;
            innerClassInfos[i] = innerClassInfo;
            /*
             * HotSpot does not perform this check. Enforcing the spec here break some applications
             * in the wild e.g. Intellij IDEA.
             */
            if (parsingContext.isStrictJavaCompliance()) {
                if (majorVersion >= JAVA_7_VERSION && innerClassInfo.innerNameIndex == 0 && outerClassIndex != 0) {
                    throw classFormatError("InnerClassesAttribute: the value of the outer_class_info_index item must be zero if the value of the inner_name_index item is zero.");
                }
            }

            if (innerClassIndex == outerClassIndex) {
                throw classFormatError("Class is both outer and inner class");
            }

            Symbol<Name> innerClassName = null;
            if (innerClassIndex != 0) {
                innerClassName = pool.classAt(innerClassIndex).getName(pool);
            }

            for (int j = 0; j < i; ++j) {
                // Inner class info is often small: better to use array lookup for startup.
                final InnerClassesAttribute.Entry otherInnerClassInfo = innerClassInfos[j];
                if (otherInnerClassInfo != null) {
                    if (innerClassIndex == otherInnerClassInfo.innerClassIndex && outerClassIndex == otherInnerClassInfo.outerClassIndex) {
                        throw classFormatError("Duplicate entry in InnerClasses attribute");
                    }
                    if (innerClassIndex == otherInnerClassInfo.innerClassIndex ||
                                    // The same class can be referenced by two different CP indices,
                                    // compare by name instead.
                                    (innerClassIndex != 0 &&
                                                    otherInnerClassInfo.innerClassIndex != 0 &&
                                                    innerClassName.equals(pool.classAt(otherInnerClassInfo.innerClassIndex).getName(pool)))) {
                        duplicateInnerClass = true;
                    }
                }
            }
        }

        if (duplicateInnerClass || hasCycles(innerClassInfos)) {
            // Mimic HotSpot: Ignore the whole InnerClasses attribute and return an empty one.
            final String cause = duplicateInnerClass
                            ? "Duplicate inner_class_info_index (class names)"
                            : "Cycle detected";
            parsingContext.getLogger().log(() -> cause + " in InnerClassesAttribute, in class " + classType);
            return new InnerClassesAttribute(name, new InnerClassesAttribute.Entry[0]);
        }

        return new InnerClassesAttribute(name, innerClassInfos);
    }

    int findInnerClassIndexEntry(InnerClassesAttribute.Entry[] entries, Symbol<Name> innerClassName) {
        for (int i = 0; i < entries.length; i++) {
            InnerClassesAttribute.Entry entry = entries[i];
            if (entry.innerClassIndex == 0) {
                continue;
            }
            // The same class can be referenced by two different CP indices, compare by name
            // instead.
            if (innerClassName == pool.classAt(entry.innerClassIndex).getName(pool)) {
                return i;
            }
        }
        return -1;
    }

    boolean hasCycles(InnerClassesAttribute.Entry[] entries) {
        int curMark = 0;
        int[] mark = new int[entries.length];
        for (int i = 0; i < entries.length; i++) {
            if (mark[i] != 0) { // already visited
                continue;
            }
            mark[i] = ++curMark;
            int v = entries[i].outerClassIndex;
            while (true) {
                if (v == 0) {
                    break;
                }
                int index = findInnerClassIndexEntry(entries, pool.classAt(v).getName(pool));
                if (index < 0) { // no edge found
                    break;
                }
                if (mark[index] == curMark) { // cycle found
                    return true;
                }
                if (mark[index] != 0) { // already visited
                    break;
                }
                mark[index] = curMark;
                v = entries[index].outerClassIndex;
            }
        }
        return false;
    }

    private StackMapTableAttribute parseStackMapTableAttribute(Symbol<Name> attributeName, int attributeSize) {
        if (verifiable) {
            return new StackMapTableAttribute(attributeName, stream.readByteArray(attributeSize));
        }
        stream.skip(attributeSize);
        return StackMapTableAttribute.EMPTY;
    }

    private NestHostAttribute parseNestHostAttribute(Symbol<Name> attributeName) throws ValidationException {
        int hostClassIndex = stream.readU2();
        pool.classAt(hostClassIndex).validate(pool);
        return new NestHostAttribute(attributeName, hostClassIndex);
    }

    private NestMembersAttribute parseNestMembers(Symbol<Name> attributeName) throws ValidationException {
        assert NestMembersAttribute.NAME.equals(attributeName);
        int numberOfClasses = stream.readU2();
        int[] classes = new int[numberOfClasses];
        for (int i = 0; i < numberOfClasses; i++) {
            int pos = stream.readU2();
            pool.classAt(pos).validate(pool);
            classes[i] = pos;
        }
        return new NestMembersAttribute(attributeName, classes);
    }

    private PermittedSubclassesAttribute parsePermittedSubclasses(Symbol<Name> attributeName) throws ValidationException {
        assert PermittedSubclassesAttribute.NAME.equals(attributeName);
        if ((classFlags & ACC_FINAL) != 0) {
            throw classFormatError("A final class may not declare a permitted subclasses attribute.");
        }
        int numberOfClasses = stream.readU2();
        if (numberOfClasses == 0) {
            return PermittedSubclassesAttribute.EMPTY;
        }
        char[] classes = new char[numberOfClasses];
        for (int i = 0; i < numberOfClasses; i++) {
            int pos = stream.readU2();
            pool.classAt(pos).validate(pool);
            classes[i] = (char) pos;
        }
        return new PermittedSubclassesAttribute(attributeName, classes);
    }

    private RecordAttribute parseRecord(Symbol<Name> recordAttributeName) throws ValidationException {
        assert RecordAttribute.NAME.equals(recordAttributeName);
        int count = stream.readU2();
        RecordAttribute.RecordComponentInfo[] components = new RecordAttribute.RecordComponentInfo[count];
        for (int i = 0; i < count; i++) {
            final int nameIndex = stream.readU2();
            final int descriptorIndex = stream.readU2();
            pool.utf8At(nameIndex).validateUTF8();
            pool.utf8At(descriptorIndex).validateType(false);
            Attribute[] componentAttributes = parseRecordComponentAttributes();
            components[i] = new RecordAttribute.RecordComponentInfo(nameIndex, descriptorIndex, componentAttributes);
        }
        return new RecordAttribute(recordAttributeName, components);
    }

    private Attribute[] parseRecordComponentAttributes() throws ValidationException {
        final int size = stream.readU2();
        Attribute[] componentAttributes = new Attribute[size];
        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Record);
        for (int j = 0; j < size; j++) {
            final Symbol<Name> attributeName = pool.symbolAtUnsafe(stream.readU2(), "attribute name");
            final int attributeSize = stream.readS4();
            Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
            componentAttributes[j] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
        }
        return componentAttributes;
    }

    private InnerClassesAttribute.Entry parseInnerClassEntry() throws ValidationException {
        int innerClassIndex = stream.readU2();
        int outerClassIndex = stream.readU2();
        int innerNameIndex = stream.readU2();
        int innerClassAccessFlags = stream.readU2();

        if ((innerClassAccessFlags & ACC_INTERFACE) != 0 && majorVersion < JAVA_6_VERSION) {
            // Set abstract bit for old class files for backward compatibility
            innerClassAccessFlags |= ACC_ABSTRACT;
        }

        if (innerClassIndex != 0 || parsingContext.getJavaVersion().java9OrLater()) {
            pool.classAt(innerClassIndex).validate(pool);
        }
        if (outerClassIndex != 0) {
            pool.classAt(outerClassIndex).validate(pool);
        }
        if (innerNameIndex != 0) {
            // Gradle (the build tool) generates classes with innerNameIndex -> empty string.
            // HotSpot does not validates the class name, only that it is a valid UTF-8 constant.
            pool.utf8At(innerNameIndex).validate(pool); // .validateClassName();
        }
        return new InnerClassesAttribute.Entry(innerClassIndex, outerClassIndex, innerNameIndex, innerClassAccessFlags);
    }

    private EnclosingMethodAttribute parseEnclosingMethodAttribute(Symbol<Name> name) {
        int classIndex = stream.readU2();
        int methodIndex = stream.readU2();
        return new EnclosingMethodAttribute(name, classIndex, methodIndex);
    }

    private CodeAttribute parseCodeAttribute(Symbol<Name> name) throws ValidationException {
        int maxStack = stream.readU2();
        int maxLocals = stream.readU2();

        final int codeLength = stream.readS4();
        if (codeLength <= 0) {
            throw classFormatError("code_length must be > than 0");
        } else if (codeLength > 0xFFFF) {
            throw classFormatError("code_length > than 64 KB");
        }

        byte[] code;
        try (DebugCloseable codeRead = CODE_READ.scope(parsingContext.getTimers())) {
            code = stream.readByteArray(codeLength);
        }
        ExceptionHandler[] entries;
        try (DebugCloseable handlers = EXCEPTION_HANDLERS.scope(parsingContext.getTimers())) {
            entries = parseExceptionHandlerEntries();
        }

        int attributeCount = stream.readU2();
        final Attribute[] codeAttributes = spawnAttributesArray(attributeCount);
        int totalLocalTableCount = 0;

        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Code);

        StackMapTableAttribute stackMapTable = null;

        for (int i = 0; i < attributeCount; i++) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName;
            attributeName = pool.symbolAtUnsafe(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();

            if (attributeName.equals(ParserNames.LineNumberTable)) {
                codeAttributes[i] = parseLineNumberTable(attributeName);
            } else if (attributeName.equals(ParserNames.LocalVariableTable)) {
                codeAttributes[i] = parseLocalVariableAttribute(attributeName, codeLength, maxLocals);
                totalLocalTableCount++;
            } else if (attributeName.equals(ParserNames.LocalVariableTypeTable)) {
                codeAttributes[i] = parseLocalVariableTypeAttribute(attributeName, codeLength, maxLocals);
            } else if (attributeName.equals(ParserNames.StackMapTable)) {
                if (stackMapTable != null) {
                    throw classFormatError("Duplicate StackMapTable attribute");
                }
                codeAttributes[i] = stackMapTable = parseStackMapTableAttribute(attributeName, attributeSize);
            } else {
                Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                // stream.skip(attributeSize);
                codeAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw classFormatError("Invalid attribute length for " + attributeName + " attribute");
            }
        }

        if (validate && totalLocalTableCount > 0) {
            validateLocalTables(codeAttributes);
        }

        return new CodeAttribute(name, maxStack, maxLocals, code, entries, codeAttributes, majorVersion);
    }

    private void validateLocalTables(Attribute[] codeAttributes) {
        if (getMajorVersion() < JAVA_1_5_VERSION) {
            return;
        }
        EconomicMap<Local, Boolean> table = EconomicMap.create(Local.localEquivalence);
        ArrayList<LocalVariableTable> typeTables = new ArrayList<>();
        for (Attribute attr : codeAttributes) {
            if (attr.getName() == ParserNames.LocalVariableTable) {
                LocalVariableTable localTable = (LocalVariableTable) attr;
                for (Local local : localTable.getLocals()) {
                    if (table.put(local, false) != null) {
                        throw classFormatError("Duplicate local in local variable table: " + local);
                    }
                }
            } else if (attr.getName() == ParserNames.LocalVariableTypeTable) {
                typeTables.add((LocalVariableTable) attr);
            }
        }
        for (LocalVariableTable typeTable : typeTables) {
            for (Local local : typeTable.getLocals()) {
                Boolean present = table.put(local, true);
                if (present == null) {
                    throw classFormatError("Local in local variable type table does not match any local variable table entry: " + local);
                }
                if (present) {
                    throw classFormatError("Duplicate local in local variable type table: " + local);
                }
            }
        }
    }

    private ExceptionHandler[] parseExceptionHandlerEntries() {
        int count = stream.readU2();
        if (count == 0) {
            return ExceptionHandler.EMPTY_ARRAY;
        }
        ExceptionHandler[] entries = new ExceptionHandler[count];
        for (int i = 0; i < count; i++) {
            int startPc = stream.readU2();
            int endPc = stream.readU2();
            int handlerPc = stream.readU2();
            int catchTypeIndex = stream.readU2();
            Symbol<Type> catchType = null;
            if (catchTypeIndex != 0) {
                catchType = parsingContext.getOrCreateTypeFromName(pool.classAt(catchTypeIndex).getName(pool));
            }
            entries[i] = new ExceptionHandler(startPc, endPc, handlerPc, catchTypeIndex, catchType);
        }
        return entries;
    }

    /**
     * Verifies that the flags for a field are valid.
     *
     * @param name the name of the field
     * @param flags the flags to test
     * @param isInterface if the field flags are being tested for an interface
     * @throws ParserException.ClassFormatError if the flags are invalid
     */
    private static void verifyFieldFlags(Symbol<Name> name, int flags, boolean isInterface) {
        boolean valid;
        if (!isInterface) {
            // Class or instance fields
            final int maskedFlags = flags & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED);

            // Make sure that flags has at most one of its ACC_PRIVATE,
            // ACC_PROTECTED bits set. That is, do a population count of these
            // bit positions corresponding to these flags and ensure that it is
            // at most 1.
            valid = maskedFlags == 0 || (maskedFlags & ~(maskedFlags - 1)) == maskedFlags;

            // A field can't be both final and volatile
            final int finalAndVolatile = ACC_FINAL | ACC_VOLATILE;
            valid = valid && ((flags & finalAndVolatile) != finalAndVolatile);
        } else {
            // interface fields must be public static final (i.e. constants), but may have
            // ACC_SYNTHETIC set
            valid = (flags & ~ACC_SYNTHETIC) == (ACC_STATIC | ACC_FINAL | ACC_PUBLIC);
        }
        if (!valid) {
            throw classFormatError(name + ": invalid field flags 0x" + Integer.toHexString(flags));
        }
    }

    private ParserField parseField(boolean isInterface) throws ValidationException {
        int fieldFlags = stream.readU2();
        int nameIndex = stream.readU2();
        int typeIndex = stream.readU2();

        final Symbol<Name> name = validateFieldName(pool.utf8At(nameIndex, "field name"));
        verifyFieldFlags(name, fieldFlags, isInterface);

        final boolean isStatic = Modifier.isStatic(fieldFlags);

        final Symbol<Type> descriptor = validateType(pool.utf8At(typeIndex, "field descriptor"), false);

        final int attributeCount = stream.readU2();
        final Attribute[] fieldAttributes = spawnAttributesArray(attributeCount);

        ConstantValueAttribute constantValue = null;
        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Field);

        for (int i = 0; i < attributeCount; ++i) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAtUnsafe(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (isStatic && attributeName.equals(ParserNames.ConstantValue)) {
                if (constantValue != null) {
                    throw classFormatError("Duplicate ConstantValue attribute");
                }
                fieldAttributes[i] = constantValue = new ConstantValueAttribute(stream.readU2());
                if (constantValue.getConstantValueIndex() == 0) {
                    throw classFormatError("Invalid ConstantValue index");
                }
                PoolConstant entry = pool.at(constantValue.getConstantValueIndex(), "constant value index");
                boolean isValid = switch (entry.tag()) {
                    case INTEGER -> TypeSymbols.getJavaKind(descriptor).isStackInt();
                    case FLOAT -> descriptor == ParserTypes._float;
                    case LONG -> descriptor == ParserTypes._long;
                    case DOUBLE -> descriptor == ParserTypes._double;
                    case STRING -> descriptor == ParserTypes.java_lang_String;
                    default -> false;
                };
                if (!isValid) {
                    throw classFormatError("Invalid ConstantValue index entry type");
                }
            } else if (attributeName.equals(ParserNames.Synthetic)) {
                fieldFlags |= ACC_SYNTHETIC;
                fieldAttributes[i] = new Attribute(attributeName, null);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (attributeName.equals(ParserNames.RuntimeVisibleAnnotations)) {
                    RuntimeVisibleAnnotationsAttribute annotations = commonAttributeParser.parseRuntimeVisibleAnnotations(attributeSize, AnnotationLocation.Field);
                    fieldFlags |= annotations.flags;
                    fieldAttributes[i] = annotations.attribute;
                } else {
                    Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                    // stream.skip(attributeSize);
                    fieldAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
                }
            } else {
                // stream.skip(attributeSize);
                fieldAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw classFormatError("Invalid attribute_length for " + attributeName + " attribute");
            }
        }

        final JavaKind kind = TypeSymbols.getJavaKind(descriptor);
        if (kind == JavaKind.Void) {
            throw classFormatError("Fields cannot be of type void");
        }

        if (constantValue != null) {

            Tag tag = pool.tagAt(constantValue.getConstantValueIndex());
            boolean valid = false;

            switch (kind) {
                case Boolean: // fall through
                case Byte: // fall through
                case Short: // fall through
                case Char: // fall through
                case Int: // fall through
                    valid = (tag == Tag.INTEGER);
                    break;
                case Float:
                    valid = (tag == Tag.FLOAT);
                    break;
                case Long:
                    valid = (tag == Tag.LONG);
                    break;
                case Double:
                    valid = (tag == Tag.DOUBLE);
                    break;
                case Object:
                    valid = (tag == Tag.STRING) && descriptor.equals(ParserTypes.java_lang_String);
                    break;
                default: {
                    throw classFormatError("Cannot have ConstantValue for fields of type " + kind);
                }
            }

            if (!valid) {
                throw classFormatError("ConstantValue attribute does not match field type");
            }
        }

        return new ParserField(fieldFlags, name, descriptor, fieldAttributes);
    }

    private ParserField[] parseFields(boolean isInterface) throws ValidationException {
        int fieldCount = stream.readU2();
        if (fieldCount == 0) {
            return ParserField.EMPTY_ARRAY;
        }
        ParserField[] fields = new ParserField[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = parseField(isInterface);
            for (int j = 0; j < i; ++j) {
                if (fields[j].getName().equals(fields[i].getName()) && fields[j].getType().equals(fields[i].getType())) {
                    throw classFormatError("Duplicate field name and signature: " + fields[j].getName() + " " + fields[j].getType());
                }
            }
        }
        return fields;
    }

    /**
     * Parses the reference to the super class. Resolving and checking the super class is done after
     * parsing the current class file so that resolution is only attempted if there are no format
     * errors in the current class file.
     */
    private Symbol<Type> parseSuperKlass() {
        int index = stream.readU2();
        if (index == 0) {
            if (!classType.equals(ParserTypes.java_lang_Object)) {
                throw classFormatError("Invalid superclass index 0");
            }
            return null;
        }
        return parsingContext.getOrCreateTypeFromName(pool.classAt(index).getName(pool));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Symbol<Type>[] parseInterfaces() {
        int interfaceCount = stream.readU2();
        if (interfaceCount == 0) {
            return Symbol.emptyArray();
        }
        Symbol<Type>[] interfaces = new Symbol[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            int interfaceIndex = stream.readU2();
            Symbol<Name> interfaceName = pool.classAt(interfaceIndex).getName(pool);
            Symbol<Type> interfaceType = parsingContext.getOrCreateTypeFromName(interfaceName);
            if (interfaceType == null) {
                throw classFormatError(classType + " contains invalid superinterface name: " + interfaceName);
            }
            if (TypeSymbols.isPrimitive(interfaceType) || TypeSymbols.isArray(interfaceType)) {
                throw classFormatError(classType + " superinterfaces cannot contain arrays nor primitives");
            }
            interfaces[i] = interfaceType;
        }
        // Check for duplicate interfaces in the interface array.
        Set<Symbol<Type>> present = new HashSet<>(interfaces.length);
        for (Symbol<Type> t : interfaces) {
            if (!present.add(t)) {
                throw classFormatError("Duplicate interface name in classfile: " + t);
            }
        }
        return interfaces;
    }

    int getMajorVersion() {
        assert majorVersion != -1;
        return majorVersion;
    }

    private static class MethodKey {
        private final Symbol<Name> methodName;
        private final Symbol<Signature> signature;
        private final int hash;

        MethodKey(ParserMethod method) {
            this.methodName = method.getName();
            this.signature = method.getSignature();
            this.hash = Objects.hash(methodName, signature);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodKey other) {
                return methodName.equals(other.methodName) && signature.equals(other.signature);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    // region Validation (throwing) methods

    // Encoding (Modified UTF8)

    private Symbol<? extends ModifiedUTF8> validateEncoding(Utf8Constant utf8Constant) throws ValidationException {
        if (!validate) {
            return utf8Constant.unsafeSymbolValue();
        }
        return utf8Constant.validateUTF8();
    }

    // Names

    private Symbol<Name> validateFieldName(Utf8Constant utf8Constant) throws ValidationException {
        if (!validate) {
            return utf8Constant.unsafeSymbolValue();
        }
        return utf8Constant.validateFieldName();
    }

    private Symbol<Name> validateMethodName(Utf8Constant utf8Constant, boolean allowClinit) throws ValidationException {
        if (!validate) {
            return utf8Constant.unsafeSymbolValue();
        }
        return utf8Constant.validateMethodName(allowClinit);
    }

    @SuppressWarnings("unused")
    private Symbol<Name> validateClassName(Utf8Constant utf8Constant) throws ValidationException {
        if (!validate) {
            return utf8Constant.unsafeSymbolValue();
        }
        return utf8Constant.validateClassName();
    }

    // Types

    private Symbol<Type> validateType(Utf8Constant utf8Constant, boolean allowVoid) throws ValidationException {
        if (!validate) {
            return utf8Constant.unsafeSymbolValue();
        }
        return utf8Constant.validateType(allowVoid);
    }

    // endregion Validation (throwing) methods
}
