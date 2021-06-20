/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.EspressoOptions.SpecCompliancyMode.STRICT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ANNOTATION;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_CALLER_SENSITIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ENUM;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_INTERFACE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_LAMBDA_FORM_COMPILED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_LAMBDA_FORM_HIDDEN;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_MODULE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_NATIVE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PRIVATE;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PROTECTED;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_PUBLIC;
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
import java.util.HashSet;
import java.util.Objects;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
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
import com.oracle.truffle.espresso.classfile.attributes.SignatureAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceDebugExtensionAttribute;
import com.oracle.truffle.espresso.classfile.attributes.SourceFileAttribute;
import com.oracle.truffle.espresso.classfile.attributes.StackMapTableAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.ModifiedUTF8;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.perf.DebugCloseable;
import com.oracle.truffle.espresso.perf.DebugTimer;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

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

    @SuppressWarnings("unused") private static final int MAJOR_VERSION_JAVA_MIN = 0;
    @SuppressWarnings("unused") private static final int MAJOR_VERSION_JAVA_MAX = JAVA_17_VERSION;

    public static final int STRICTER_ACCESS_CTRL_CHECK_VERSION = JAVA_1_5_VERSION;
    public static final int STACKMAP_ATTRIBUTE_MAJOR_VERSION = JAVA_6_VERSION;
    public static final int INVOKEDYNAMIC_MAJOR_VERSION = JAVA_7_VERSION;
    public static final int NO_RELAX_ACCESS_CTRL_CHECK_VERSION = JAVA_8_VERSION;
    public static final int DYNAMICCONSTANT_MAJOR_VERSION = JAVA_11_VERSION;

    public static final char JAVA_MIN_SUPPORTED_VERSION = JAVA_1_1_VERSION;
    public static final char JAVA_MAX_SUPPORTED_VERSION = JAVA_11_VERSION;
    public static final char JAVA_MAX_SUPPORTED_MINOR_VERSION = 0;

    private final ClasspathFile classfile;

    private final String requestedClassType;

    private final EspressoContext context;

    private final ClassfileStream stream;

    private final StaticObject[] constantPoolPatches;

    private Symbol<Type> classType;

    private int minorVersion;
    private int majorVersion;
    private int classFlags;

    private int maxBootstrapMethodAttrIndex = -1;
    private Tag badConstantSeen;

    StaticObject loader;

    private ConstantPool pool;

    private ClassfileParser(ClassfileStream stream, StaticObject loader, String requestedClassType, EspressoContext context, StaticObject[] constantPoolPatches) {
        this.requestedClassType = requestedClassType;
        this.context = context;
        this.classfile = null;
        this.stream = Objects.requireNonNull(stream);
        this.constantPoolPatches = constantPoolPatches;
        this.loader = loader;
    }

    // Note: only used for reading the class name from class bytes
    private ClassfileParser(ClassfileStream stream, EspressoContext context) {
        this(stream, null, "", context, null);
    }

    void handleBadConstant(Tag tag, ClassfileStream s) {
        if (tag == Tag.MODULE || tag == Tag.PACKAGE) {
            if (majorVersion >= JAVA_9_VERSION) {
                s.readU2();
                badConstantSeen = tag;
                throw stream.classFormatError("Illegal constant tag for a class file: %d. Should appear only in module info files.", tag.getValue());
            }
        }
        throw stream.classFormatError("Unknown constant tag %d", tag.getValue());
    }

    void updateMaxBootstrapMethodAttrIndex(int bsmAttrIndex) {
        if (maxBootstrapMethodAttrIndex < bsmAttrIndex) {
            maxBootstrapMethodAttrIndex = bsmAttrIndex;
        }
    }

    void checkInvokeDynamicSupport(Tag tag) {
        if (majorVersion < INVOKEDYNAMIC_MAJOR_VERSION) {
            throw stream.classFormatError("Class file version does not support constant tag %d", tag.getValue());
        }
    }

    void checkDynamicConstantSupport(Tag tag) {
        if (majorVersion < DYNAMICCONSTANT_MAJOR_VERSION) {
            throw stream.classFormatError("Class file version does not support constant tag %d", tag.getValue());
        }
    }

    public static ParserKlass parse(ClassfileStream stream, StaticObject loader, String requestedClassName, EspressoContext context) {
        return parse(stream, loader, requestedClassName, context, null);
    }

    public static ParserKlass parse(ClassfileStream stream, StaticObject loader, String requestedClassName, EspressoContext context, @Host(Object[].class) StaticObject[] constantPoolPatches) {
        return new ClassfileParser(stream, loader, requestedClassName, context, constantPoolPatches).parseClass();
    }

    private ParserKlass parseClass() {
        try (DebugCloseable parse = KLASS_PARSE.scope(context.getTimers())) {
            return parseClassImpl();
        } catch (EspressoException e) {
            throw e;
        } catch (Throwable e) {
            context.getLogger().severe("Unexpected host exception " + e + " thrown during class parsing.");
            throw e;
        }
    }

    private void readMagic() {
        int magic = stream.readS4();
        if (magic != MAGIC) {
            throw ConstantPool.classFormatError("Invalid magic number 0x" + Integer.toHexString(magic));
        }
    }

    /**
     * Verifies that the class file version is supported.
     * 
     * @param major the major version number
     * @param minor the minor version number
     */
    private void verifyVersion(int major, int minor) {
        if (context.getJavaVersion().java8OrEarlier()) {
            versionCheck8(context.getJavaVersion().classFileVersion(), major, minor);
        } else {
            versionCheck11(context.getJavaVersion().classFileVersion(), major, minor);
        }
    }

    /**
     * HotSpot rules (8): A legal major_version.minor_version must be one of the following:
     *
     * <li>Major_version >= 45 and major_version <= current_major_version, any minor version.
     * <li>Major_version = current_major_version and minor_version <= MAX_SUPPORTED_MINOR (= 0).
     */
    private static void versionCheck8(int maxMajor, int major, int minor) {
        if (major < JAVA_MIN_SUPPORTED_VERSION ||
                        major > maxMajor ||
                        (major == maxMajor && minor > JAVA_MAX_SUPPORTED_MINOR_VERSION)) {
            throw unsupportedClassVersionError("Unsupported major.minor version " + major + "." + minor);
        }
    }

    /**
     * HotSpot comment (11): A legal major_version.minor_version must be one of the following:
     *
     * <li>Major_version = 45, any minor_version.
     * <li>Major_version >= 46 and major_version <= current_major_version and minor_version = 0.
     * <li>Major_version = current_major_version and minor_version = 65535 and --enable-preview is
     * present.
     */
    private static void versionCheck11(int maxMajor, int major, int minor) {
        if (major < JAVA_MIN_SUPPORTED_VERSION ||
                        major > maxMajor ||
                        major != JAVA_1_1_VERSION && minor != 0 ||
                        (major == maxMajor) && (minor > JAVA_MAX_SUPPORTED_MINOR_VERSION)) {
            throw unsupportedClassVersionError("Unsupported major.minor version " + major + "." + minor);
        }
    }

    private static EspressoException unsupportedClassVersionError(String message) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_UnsupportedClassVersionError, message);
    }

    /**
     * Verifies that the flags for a class are valid.
     *
     * @param flags the flags to test
     * @throws ClassFormatError if the flags are invalid
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
            throw ConstantPool.classFormatError("Invalid class flags 0x" + Integer.toHexString(flags));
        }
    }

    private ParserKlass parseClassImpl() {
        readMagic();

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        verifyVersion(majorVersion, minorVersion);

        try (DebugCloseable closeable = CONSTANT_POOL.scope(context.getTimers())) {
            if (constantPoolPatches == null) {
                this.pool = ConstantPool.parse(context.getLanguage(), stream, this, majorVersion, minorVersion);
            } else {
                this.pool = ConstantPool.parse(context.getLanguage(), stream, this, constantPoolPatches, context, majorVersion, minorVersion);
            }
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
            throw ConstantPool.noClassDefFoundError(classType + " is not a class because access_flag ACC_MODULE is set");
        }

        if (badConstantSeen != null) {
            // Do not throw CFE until after the access_flags are checked because if
            // ACC_MODULE is set in the access flags, then NCDFE must be thrown, not CFE.
            // https://bugs.openjdk.java.net/browse/JDK-8175383
            throw ConstantPool.classFormatError(String.format("Unknown constant tag %s [in class file %s]", badConstantSeen, classfile));
        }

        verifyClassFlags(classFlags, majorVersion);

        // this class
        int thisKlassIndex = stream.readU2();

        Symbol<Name> thisKlassName = pool.classAt(thisKlassIndex).getName(pool);

        final boolean isInterface = Modifier.isInterface(classFlags);

        // TODO(peterssen): Verify class names.

        Symbol<Type> thisKlassType = context.getTypes().fromName(thisKlassName);
        if (Types.isPrimitive(thisKlassType) || Types.isArray(thisKlassType)) {
            throw ConstantPool.classFormatError(".this_class cannot be array nor primitive " + classType);
        }

        // Update classType which could be null previously to reflect the name in the constant pool.
        classType = thisKlassType;

        // Checks if name in class file matches requested name
        if (requestedClassType != null && !requestedClassType.equals(classType.toString())) {
            throw ConstantPool.noClassDefFoundError(classType + " (wrong name: " + requestedClassType + ")");
        }

        Symbol<Type> superKlass = parseSuperKlass();

        if (!Type.java_lang_Object.equals(classType) && superKlass == null) {
            throw ConstantPool.classFormatError("Class " + classType + " must have a superclass");
        }

        if (isInterface && !Type.java_lang_Object.equals(superKlass)) {
            throw ConstantPool.classFormatError("Interface " + classType + " must extend java.lang.Object");
        }

        Symbol<Type>[] superInterfaces;
        try (DebugCloseable closeable = PARSE_INTERFACES.scope(context.getTimers())) {
            superInterfaces = parseInterfaces();
        }
        final ParserField[] fields;
        try (DebugCloseable closeable = PARSE_FIELD.scope(context.getTimers())) {
            fields = parseFields(isInterface);
        }
        final ParserMethod[] methods;
        try (DebugCloseable closeable = PARSE_METHODS.scope(context.getTimers())) {
            methods = parseMethods(isInterface);
        }
        final Attribute[] attributes;
        try (DebugCloseable closeable = PARSE_CLASSATTR.scope(context.getTimers())) {
            attributes = parseClassAttributes();
        }

        // Ensure there are no trailing bytes
        stream.checkEndOfFile();

        return new ParserKlass(pool, classFlags, thisKlassName, thisKlassType, superKlass, superInterfaces, methods, fields, attributes, thisKlassIndex);
    }

    public static Symbol<Symbol.Name> getClassName(byte[] bytes, EspressoContext context) {
        return new ClassfileParser(new ClassfileStream(bytes, null), context).getClassName();
    }

    private Symbol<Symbol.Name> getClassName() {

        readMagic();

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        verifyVersion(majorVersion, minorVersion);

        try (DebugCloseable closeable = CONSTANT_POOL.scope(context.getTimers())) {
            if (constantPoolPatches == null) {
                this.pool = ConstantPool.parse(context.getLanguage(), stream, this, majorVersion, minorVersion);
            } else {
                this.pool = ConstantPool.parse(context.getLanguage(), stream, this, constantPoolPatches, context, majorVersion, minorVersion);
            }
        }

        // JVM_ACC_MODULE is defined in JDK-9 and later.
        if (majorVersion >= JAVA_9_VERSION) {
            classFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS | ACC_MODULE);
        } else {
            classFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS);
        }

        int thisKlassIndex = stream.readU2();

        return pool.classAt(thisKlassIndex).getName(pool);
    }

    private ParserMethod[] parseMethods(boolean isInterface) {
        int methodCount = stream.readU2();
        if (methodCount == 0) {
            return ParserMethod.EMPTY_ARRAY;
        }
        ParserMethod[] methods = new ParserMethod[methodCount];
        /*
         * Classes in general have lots of methods: use a hash set rather than array lookup for dup
         * check.
         */
        final HashSet<MethodKey> dup = new HashSet<>(methodCount);
        for (int i = 0; i < methodCount; ++i) {
            ParserMethod method;
            try (DebugCloseable closeable = PARSE_SINGLE_METHOD.scope(context.getTimers())) {
                method = parseMethod(isInterface);
            }
            methods[i] = method;
            try (DebugCloseable closeable = NO_DUP_CHECK.scope(context.getTimers())) {
                if (!dup.add(new MethodKey(method))) {
                    throw ConstantPool.classFormatError("Duplicate method name and signature: " + method.getName() + " " + method.getSignature());
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
     * @throws ClassFormatError if the flags are invalid
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
            throw ConstantPool.classFormatError("Invalid method flags 0x" + Integer.toHexString(flags));
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

    public enum InfoType {
        Class(classAnnotations),
        Method(methodAnnotations),
        Field(fieldAnnotations),
        Code(codeAnnotations);

        final int annotations;

        InfoType(int annotations) {
            this.annotations = annotations;
        }

        boolean supports(int annotation) {
            return (annotations & annotation) != 0;
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

        Attribute parseCommonAttribute(Symbol<Name> attributeName, int attributeSize) {
            if (infoType.supports(RUNTIME_VISIBLE_ANNOTATIONS) && attributeName.equals(Name.RuntimeVisibleAnnotations)) {
                if (runtimeVisibleAnnotations != null) {
                    throw ConstantPool.classFormatError("Duplicate RuntimeVisibleAnnotations attribute");
                }
                return runtimeVisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_VISIBLE_TYPE_ANNOTATIONS) && attributeName.equals(Name.RuntimeVisibleTypeAnnotations)) {
                if (runtimeVisibleTypeAnnotations != null) {
                    throw ConstantPool.classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                }
                return runtimeVisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_ANNOTATIONS) && attributeName.equals(Name.RuntimeInvisibleAnnotations)) {
                if (runtimeInvisibleAnnotations != null) {
                    throw ConstantPool.classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                }
                return runtimeInvisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_TYPE_ANNOTATIONS) && attributeName.equals(Name.RuntimeInvisibleTypeAnnotations)) {
                if (runtimeInvisibleTypeAnnotations != null) {
                    throw ConstantPool.classFormatError("Duplicate RuntimeInvisibleTypeAnnotations attribute");
                }
                return runtimeInvisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS) && attributeName.equals(Name.RuntimeVisibleParameterAnnotations)) {
                if (runtimeVisibleParameterAnnotations != null) {
                    throw ConstantPool.classFormatError("Duplicate RuntimeVisibleParameterAnnotations attribute");
                }
                return runtimeVisibleParameterAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(RUNTIME_INVISIBLE_PARAMETER_ANNOTATIONS) && attributeName.equals(Name.RuntimeInvisibleParameterAnnotations)) {
                if (runtimeInvisibleParameterAnnotations != null) {
                    throw ConstantPool.classFormatError("Duplicate RuntimeVisibleParameterAnnotations attribute");
                }
                return runtimeInvisibleParameterAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(ANNOTATION_DEFAULT) && attributeName.equals(Name.AnnotationDefault)) {
                if (annotationDefault != null) {
                    throw ConstantPool.classFormatError("Duplicate AnnotationDefault attribute");
                }
                return annotationDefault = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (infoType.supports(SIGNATURE) && attributeName.equals(Name.Signature)) {
                if (context.getJavaVersion().java9OrLater() && signature != null) {
                    throw ConstantPool.classFormatError("Duplicate AnnotationDefault attribute");
                }
                return signature = parseSignatureAttribute(attributeName);
            }
            return null;
        }
    }

    private ParserMethod parseMethod(boolean isInterface) {
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

        try (DebugCloseable closeable = METHOD_INIT.scope(context.getTimers())) {

            try (DebugCloseable nameCheck = NAME_CHECK.scope(context.getTimers())) {
                pool.utf8At(nameIndex).validateMethodName(true);
                name = pool.symbolAt(nameIndex, "method name");

                if (name.equals(Name._clinit_)) {
                    // Class and interface initialization methods (3.9) are called
                    // implicitly by the Java virtual machine; the value of their
                    // access_flags item is ignored except for the settings of the
                    // ACC_STRICT flag.
                    if (majorVersion < JAVA_7_VERSION) {
                        // Backwards compatibility.
                        methodFlags = ACC_STATIC;
                    } else if ((methodFlags & ACC_STATIC) == ACC_STATIC) {
                        methodFlags &= (ACC_STRICT | ACC_STATIC);
                    } else if (context.getJavaVersion().java9OrLater()) {
                        throw ConstantPool.classFormatError("Method <clinit> is not static.");
                    }
                    // extraFlags = INITIALIZER | methodFlags;
                    isClinit = true;
                } else if (name.equals(Name._init_)) {
                    if (isInterface) {
                        throw ConstantPool.classFormatError("Method <init> is not valid in an interface.");
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
            try (DebugCloseable signatureCheck = SIGNATURE_CHECK.scope(context.getTimers())) {
                // Checks for void method if init or clinit.
                /*
                 * Obtain slot number for the signature. Forces a validation, but better in startup
                 * than going twice through the sequence, once for validation, once for slots.
                 */
                int slots = pool.utf8At(signatureIndex).validateSignatureGetSlots(isInit || isClinit);

                signature = Signatures.check(pool.symbolAt(signatureIndex, "method descriptor"));
                if (isClinit && majorVersion >= JAVA_7_VERSION) {
                    // Checks clinit takes no arguments.
                    if (!signature.equals(Signature._void)) {
                        throw ConstantPool.classFormatError("Method <clinit> has invalid signature: " + signature);
                    }
                }

                if (slots + (isStatic ? 0 : 1) > 255) {
                    throw ConstantPool.classFormatError("Too many arguments in method signature: " + signature);
                }

                if (name.equals(Name.finalize) && signature.equals(Signature._void) && !Modifier.isStatic(methodFlags) && !Type.java_lang_Object.equals(classType)) {
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

        Attribute runtimeVisibleAnnotations = null;
        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Method);

        MethodParametersAttribute methodParameters = null;

        for (int i = 0; i < attributeCount; ++i) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (attributeName.equals(Name.Code)) {
                if (codeAttribute != null) {
                    throw ConstantPool.classFormatError("Duplicate Code attribute");
                }
                try (DebugCloseable code = CODE_PARSE.scope(context.getTimers())) {
                    methodAttributes[i] = codeAttribute = parseCodeAttribute(attributeName);
                }
            } else if (attributeName.equals(Name.Exceptions)) {
                if (checkedExceptions != null) {
                    throw ConstantPool.classFormatError("Duplicate Exceptions attribute");
                }
                methodAttributes[i] = checkedExceptions = parseExceptions(attributeName);
            } else if (attributeName.equals(Name.Synthetic)) {
                methodFlags |= ACC_SYNTHETIC;
                methodAttributes[i] = checkedExceptions = new Attribute(attributeName, null);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (attributeName.equals(Name.RuntimeVisibleAnnotations)) {
                    if (runtimeVisibleAnnotations != null) {
                        throw ConstantPool.classFormatError("Duplicate RuntimeVisibleAnnotations attribute");
                    }
                    // Check if java.lang.invoke.LambdaForm.Compiled is present here.
                    byte[] data = stream.readByteArray(attributeSize);
                    ClassfileStream subStream = new ClassfileStream(data, this.classfile);
                    int count = subStream.readU2();
                    for (int j = 0; j < count; j++) {
                        int typeIndex = parseAnnotation(subStream);
                        Utf8Constant constant = pool.utf8At(typeIndex, "annotation type");
                        // Validation of the type is done at runtime by guest java code.
                        Symbol<Type> annotType = constant.value();
                        if (Type.java_lang_invoke_LambdaForm$Compiled.equals(annotType)) {
                            methodFlags |= ACC_LAMBDA_FORM_COMPILED;
                        } else if (Type.java_lang_invoke_LambdaForm$Hidden.equals(annotType)) {
                            methodFlags |= ACC_LAMBDA_FORM_HIDDEN;
                        } else if (Type.sun_reflect_CallerSensitive.equals(annotType) ||
                                        Type.jdk_internal_reflect_CallerSensitive.equals(annotType)) {
                            methodFlags |= ACC_CALLER_SENSITIVE;
                        }
                    }
                    methodAttributes[i] = runtimeVisibleAnnotations = new Attribute(attributeName, data);
                } else if (attributeName.equals(Name.MethodParameters)) {
                    if (methodParameters != null) {
                        throw ConstantPool.classFormatError("Duplicate MethodParameters attribute");
                    }
                    methodAttributes[i] = methodParameters = parseMethodParameters(attributeName);
                } else {
                    Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                    // stream.skip(attributeSize);
                    methodAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;

                }
            } else {
                // stream.skip(attributeSize);
                methodAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            final int distance = stream.getPosition() - startPosition;
            if (attributeSize != distance) {
                final String message = "Invalid attribute_length for " + attributeName + " attribute (reported " + attributeSize + " != parsed " + distance + ")";
                throw ConstantPool.classFormatError(message);
            }
        }

        if (Modifier.isAbstract(methodFlags) || Modifier.isNative(methodFlags)) {
            if (codeAttribute != null) {
                throw ConstantPool.classFormatError("Code attribute supplied for native or abstract method");
            }
        } else {
            if (codeAttribute == null) {
                throw ConstantPool.classFormatError("Missing Code attribute");
            }
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
            /* elementNameIndex */ subStream.readU2();
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
                /* constValueIndex */ subStream.readU2();
                break;
            case 'e':
                /* typeNameIndex */ subStream.readU2();
                /* constNameIndex */ subStream.readU2();
                break;
            case 'c':
                /* classInfoIndex */ subStream.readU2();
                break;
            case '@':
                /* ignore */ parseAnnotation(subStream);
                break;
            case '[':
                int numValues = subStream.readU2();
                for (int i = 0; i < numValues; i++) {
                    skipElementValue(subStream);
                }
                break;
            default:
                throw ConstantPool.classFormatError("Invalid annotation tag: " + tag);
        }

    }

    private Attribute[] parseClassAttributes() {
        int attributeCount = stream.readU2();
        if (attributeCount == 0) {
            if (maxBootstrapMethodAttrIndex >= 0) {
                throw ConstantPool.classFormatError("BootstrapMethods attribute is missing");
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

        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Class);

        final Attribute[] classAttributes = spawnAttributesArray(attributeCount);
        for (int i = 0; i < attributeCount; i++) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (attributeName.equals(Name.SourceFile)) {
                if (sourceFileName != null) {
                    throw ConstantPool.classFormatError("Duplicate SourceFile attribute");
                }
                classAttributes[i] = sourceFileName = parseSourceFileAttribute(attributeName);
            } else if (attributeName.equals(Name.SourceDebugExtension)) {
                if (sourceDebugExtensionAttribute != null) {
                    throw ConstantPool.classFormatError("Duplicate SourceDebugExtension attribute");
                }
                classAttributes[i] = sourceDebugExtensionAttribute = parseSourceDebugExtensionAttribute(attributeName, attributeSize);
            } else if (attributeName.equals(Name.Synthetic)) {
                classFlags |= ACC_SYNTHETIC;
                classAttributes[i] = new Attribute(attributeName, null);
            } else if (attributeName.equals(Name.InnerClasses)) {
                if (innerClasses != null) {
                    throw ConstantPool.classFormatError("Duplicate InnerClasses attribute");
                }
                classAttributes[i] = innerClasses = parseInnerClasses(attributeName);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (majorVersion >= JAVA_7_VERSION && attributeName.equals(Name.BootstrapMethods)) {
                    if (bootstrapMethods != null) {
                        throw ConstantPool.classFormatError("Duplicate BootstrapMethods attribute");
                    }
                    classAttributes[i] = bootstrapMethods = parseBootstrapMethods(attributeName);
                } else if (attributeName.equals(Name.EnclosingMethod)) {
                    if (enclosingMethod != null) {
                        throw ConstantPool.classFormatError("Duplicate EnclosingMethod attribute");
                    }
                    classAttributes[i] = enclosingMethod = parseEnclosingMethodAttribute(attributeName);
                } else if (majorVersion >= JAVA_11_VERSION && attributeName.equals(Name.NestHost)) {
                    if (nestHost != null) {
                        throw ConstantPool.classFormatError("Duplicate NestHost attribute");
                    }
                    if (nestMembers != null) {
                        throw ConstantPool.classFormatError("Classfile cannot have both a nest members and a nest host attribute.");
                    }
                    if (attributeSize != 2) {
                        throw ConstantPool.classFormatError("Attribute length of NestHost must be 2");
                    }
                    classAttributes[i] = nestHost = parseNestHostAttribute(attributeName);
                } else if (majorVersion >= JAVA_11_VERSION && attributeName.equals(Name.NestMembers)) {
                    if (nestMembers != null) {
                        throw ConstantPool.classFormatError("Duplicate NestMembers attribute");
                    }
                    if (nestHost != null) {
                        throw ConstantPool.classFormatError("Classfile cannot have both a nest members and a nest host attribute.");
                    }
                    classAttributes[i] = nestMembers = parseNestMembers(attributeName);
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
                throw ConstantPool.classFormatError("Invalid attribute length for " + attributeName + " attribute");
            }
        }

        if (maxBootstrapMethodAttrIndex >= 0 && bootstrapMethods == null) {
            throw ConstantPool.classFormatError("BootstrapMethods attribute is missing");
        }

        return classAttributes;
    }

    private SourceFileAttribute parseSourceFileAttribute(Symbol<Name> name) {
        assert Name.SourceFile.equals(name);
        int sourceFileIndex = stream.readU2();
        return new SourceFileAttribute(name, sourceFileIndex);
    }

    private SourceDebugExtensionAttribute parseSourceDebugExtensionAttribute(Symbol<Name> name, int attributeLength) {
        assert Name.SourceDebugExtension.equals(name);
        byte[] debugExBytes = stream.readByteArray(attributeLength);

        String debugExtension = null;
        try {
            debugExtension = ModifiedUtf8.toJavaString(debugExBytes);
        } catch (IOException e) {
            // not able to convert the debug extension bytes to Java String
            // this isn't critical, so we'll just mark as null
        }
        return new SourceDebugExtensionAttribute(name, debugExtension);
    }

    private LineNumberTableAttribute parseLineNumberTable(Symbol<Name> name) {
        assert Name.LineNumberTable.equals(name);
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

    private LocalVariableTable parseLocalVariableAttribute(Symbol<Name> name) {
        assert Name.LocalVariableTable.equals(name);
        return parseLocalVariableTable(name);
    }

    private LocalVariableTable parseLocalVariableTypeAttribute(Symbol<Name> name) {
        assert Name.LocalVariableTypeTable.equals(name);
        return parseLocalVariableTable(name);
    }

    private LocalVariableTable parseLocalVariableTable(Symbol<Name> name) {
        int entryCount = stream.readU2();
        if (entryCount == 0) {
            return LocalVariableTable.EMPTY;
        }
        Local[] locals = new Local[entryCount];
        for (int i = 0; i < entryCount; i++) {
            int bci = stream.readU2();
            int length = stream.readU2();
            int nameIndex = stream.readU2();
            int descIndex = stream.readU2();
            int slot = stream.readU2();

            Utf8Constant poolName = pool.utf8At(nameIndex);
            Utf8Constant typeName = pool.utf8At(descIndex);
            locals[i] = new Local(poolName, typeName, bci, bci + length, slot);

        }
        return new LocalVariableTable(name, locals);
    }

    private SignatureAttribute parseSignatureAttribute(Symbol<Name> name) {
        assert Name.Signature.equals(name);
        int signatureIndex = stream.readU2();
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
        assert Name.Exceptions.equals(name);
        int entryCount = stream.readU2();
        int[] entries = new int[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int index = stream.readU2();
            if (index >= pool.length()) {
                throw ConstantPool.classFormatError("Invalid exception_index_table: out of bounds.");
            }
            if (index == 0) {
                throw ConstantPool.classFormatError("Invalid exception_index_table: 0.");
            }
            if (pool.tagAt(index) != Tag.CLASS) {
                throw ConstantPool.classFormatError("Invalid exception_index_table: not a CONSTANT_Class_info structure.");
            }
            entries[i] = index;
        }
        return new ExceptionsAttribute(name, entries);
    }

    private BootstrapMethodsAttribute parseBootstrapMethods(Symbol<Name> name) {
        int entryCount = stream.readU2();
        if (maxBootstrapMethodAttrIndex >= entryCount) {
            throw ConstantPool.classFormatError("Invalid bootstrapMethod index: " + maxBootstrapMethodAttrIndex + ", actual bootstrap methods size: " + entryCount);
        }
        BootstrapMethodsAttribute.Entry[] entries = new BootstrapMethodsAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int bootstrapMethodRef = stream.readU2();
            if (bootstrapMethodRef == 0) {
                throw ConstantPool.classFormatError("Invalid bootstrapMethodRefIndex: 0");
            }
            if (bootstrapMethodRef >= pool.length()) {
                throw ConstantPool.classFormatError("Invalid bootstrapMethodRefIndex: out of bounds.");
            }
            if (pool.tagAt(bootstrapMethodRef) != Tag.METHODHANDLE) {
                throw ConstantPool.classFormatError("Invalid bootstrapMethodRefIndex: not a CONSTANT_MethodHandle_info structure.");
            }
            if (maxBootstrapMethodAttrIndex >= entryCount) {
                throw ConstantPool.classFormatError("bootstrap_method_attr_index is greater than maximum valid index in the BootstrapMethods attribute.");
            }
            int numBootstrapArguments = stream.readU2();
            char[] bootstrapArguments = new char[numBootstrapArguments];
            for (int j = 0; j < numBootstrapArguments; ++j) {
                char cpIndex = (char) stream.readU2();
                if (!pool.tagAt(cpIndex).isLoadable()) {
                    throw ConstantPool.classFormatError("Invalid constant pool constant for BootstrapMethodAttribute. Not a loadable constant");
                }
                bootstrapArguments[j] = cpIndex;
            }
            entries[i] = new BootstrapMethodsAttribute.Entry((char) bootstrapMethodRef, bootstrapArguments);
        }
        return new BootstrapMethodsAttribute(name, entries);
    }

    private InnerClassesAttribute parseInnerClasses(Symbol<Name> name) {
        assert Name.InnerClasses.equals(name);
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
            if (context.SpecCompliancyMode == STRICT) {
                if (majorVersion >= JAVA_7_VERSION && innerClassInfo.innerNameIndex == 0 && outerClassIndex != 0) {
                    throw ConstantPool.classFormatError("InnerClassesAttribute: the value of the outer_class_info_index item must be zero if the value of the inner_name_index item is zero.");
                }
            }

            if (innerClassIndex == outerClassIndex) {
                throw ConstantPool.classFormatError("Class is both outer and inner class");
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
                        throw ConstantPool.classFormatError("Duplicate entry in InnerClasses attribute");
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
            context.getLogger().warning(cause + " in InnerClassesAttribute, in class " + classType);
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
        if (context.needsVerify(loader)) {
            return new StackMapTableAttribute(attributeName, stream.readByteArray(attributeSize));
        }
        stream.skip(attributeSize);
        return StackMapTableAttribute.EMPTY;
    }

    private NestHostAttribute parseNestHostAttribute(Symbol<Name> attributeName) {
        int hostClassIndex = stream.readU2();
        pool.classAt(hostClassIndex).validate(pool);
        return new NestHostAttribute(attributeName, hostClassIndex);
    }

    private NestMembersAttribute parseNestMembers(Symbol<Name> attributeName) {
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

    private InnerClassesAttribute.Entry parseInnerClassEntry() {
        int innerClassIndex = stream.readU2();
        int outerClassIndex = stream.readU2();
        int innerNameIndex = stream.readU2();
        int innerClassAccessFlags = stream.readU2();

        if ((innerClassAccessFlags & ACC_INTERFACE) != 0 && majorVersion < JAVA_6_VERSION) {
            // Set abstract bit for old class files for backward compatibility
            innerClassAccessFlags |= ACC_ABSTRACT;
        }

        if (innerClassIndex != 0 || context.getJavaVersion().java9OrLater()) {
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

    private CodeAttribute parseCodeAttribute(Symbol<Name> name) {
        int maxStack = stream.readU2();
        int maxLocals = stream.readU2();

        final int codeLength = stream.readS4();
        if (codeLength <= 0) {
            throw ConstantPool.classFormatError("code_length must be > than 0");
        } else if (codeLength > 0xFFFF) {
            throw ConstantPool.classFormatError("code_length > than 64 KB");
        }

        byte[] code;
        try (DebugCloseable codeRead = CODE_READ.scope(context.getTimers())) {
            code = stream.readByteArray(codeLength);
        }
        ExceptionHandler[] entries;
        try (DebugCloseable handlers = EXCEPTION_HANDLERS.scope(context.getTimers())) {
            entries = parseExceptionHandlerEntries();
        }

        int attributeCount = stream.readU2();
        final Attribute[] codeAttributes = spawnAttributesArray(attributeCount);

        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Code);

        StackMapTableAttribute stackMapTable = null;

        for (int i = 0; i < attributeCount; i++) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName;
            attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();

            if (attributeName.equals(Name.LineNumberTable)) {
                codeAttributes[i] = parseLineNumberTable(attributeName);
            } else if (attributeName.equals(Name.LocalVariableTable)) {
                codeAttributes[i] = parseLocalVariableAttribute(attributeName);
            } else if (attributeName.equals(Name.LocalVariableTypeTable)) {
                codeAttributes[i] = parseLocalVariableTypeAttribute(attributeName);
            } else if (attributeName.equals(Name.StackMapTable)) {
                if (stackMapTable != null) {
                    throw ConstantPool.classFormatError("Duplicate StackMapTable attribute");
                }
                codeAttributes[i] = stackMapTable = parseStackMapTableAttribute(attributeName, attributeSize);
            } else {
                Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                // stream.skip(attributeSize);
                codeAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw ConstantPool.classFormatError("Invalid attribute length for " + attributeName + " attribute");
            }
        }

        return new CodeAttribute(name, maxStack, maxLocals, code, entries, codeAttributes, majorVersion);
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
                catchType = context.getTypes().fromName(pool.classAt(catchTypeIndex).getName(pool));
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
     * @throws ClassFormatError if the flags are invalid
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
            throw ConstantPool.classFormatError(name + ": invalid field flags 0x" + Integer.toHexString(flags));
        }
    }

    private ParserField parseField(boolean isInterface) {
        int fieldFlags = stream.readU2();
        int nameIndex = stream.readU2();
        int typeIndex = stream.readU2();

        pool.utf8At(nameIndex).validateFieldName();
        final Symbol<Name> name = pool.symbolAt(nameIndex, "field name");
        verifyFieldFlags(name, fieldFlags, isInterface);

        final boolean isStatic = Modifier.isStatic(fieldFlags);

        pool.utf8At(typeIndex).validateType(false);
        Symbol<ModifiedUTF8> rawDescriptor = pool.symbolAt(typeIndex, "field descriptor");
        final Symbol<Type> descriptor = Types.fromSymbol(rawDescriptor);
        if (descriptor == null) {
            throw ConstantPool.classFormatError("Invalid descriptor: " + rawDescriptor);
        }

        final int attributeCount = stream.readU2();
        final Attribute[] fieldAttributes = spawnAttributesArray(attributeCount);

        ConstantValueAttribute constantValue = null;
        CommonAttributeParser commonAttributeParser = new CommonAttributeParser(InfoType.Field);

        for (int i = 0; i < attributeCount; ++i) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (isStatic && attributeName.equals(Name.ConstantValue)) {
                if (constantValue != null) {
                    throw ConstantPool.classFormatError("Duplicate ConstantValue attribute");
                }
                fieldAttributes[i] = constantValue = new ConstantValueAttribute(stream.readU2());
                if (constantValue.getConstantValueIndex() == 0) {
                    throw ConstantPool.classFormatError("Invalid ConstantValue index");
                }
            } else if (attributeName.equals(Name.Synthetic)) {
                fieldFlags |= ACC_SYNTHETIC;
                fieldAttributes[i] = new Attribute(attributeName, null);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                Attribute attr = commonAttributeParser.parseCommonAttribute(attributeName, attributeSize);
                // stream.skip(attributeSize);
                fieldAttributes[i] = attr == null ? new Attribute(attributeName, stream.readByteArray(attributeSize)) : attr;
            } else {
                // stream.skip(attributeSize);
                fieldAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw ConstantPool.classFormatError("Invalid attribute_length for " + attributeName + " attribute");
            }
        }

        final JavaKind kind = Types.getJavaKind(descriptor);
        if (kind == JavaKind.Void) {
            throw ConstantPool.classFormatError("Fields cannot be of type void");
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
                    valid = (tag == Tag.STRING) && descriptor.equals(Type.java_lang_String);
                    break;
                default: {
                    throw ConstantPool.classFormatError("Cannot have ConstantValue for fields of type " + kind);
                }
            }

            if (!valid) {
                throw ConstantPool.classFormatError("ConstantValue attribute does not match field type");
            }
        }

        return new ParserField(fieldFlags, name, descriptor, fieldAttributes);
    }

    private ParserField[] parseFields(boolean isInterface) {
        int fieldCount = stream.readU2();
        if (fieldCount == 0) {
            return ParserField.EMPTY_ARRAY;
        }
        ParserField[] fields = new ParserField[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = parseField(isInterface);
            for (int j = 0; j < i; ++j) {
                if (fields[j].getName().equals(fields[i].getName()) && fields[j].getType().equals(fields[i].getType())) {
                    throw ConstantPool.classFormatError("Duplicate field name and signature: " + fields[j].getName() + " " + fields[j].getType());
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
            if (!classType.equals(Type.java_lang_Object)) {
                throw ConstantPool.classFormatError("Invalid superclass index 0");
            }
            return null;
        }
        return context.getTypes().fromName(pool.classAt(index).getName(pool));
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
            Symbol<Type> interfaceType = context.getTypes().fromName(interfaceName);
            if (interfaceType == null) {
                throw ConstantPool.classFormatError(classType + " contains invalid superinterface name: " + interfaceName);
            }
            if (Types.isPrimitive(interfaceType) || Types.isArray(interfaceType)) {
                throw ConstantPool.classFormatError(classType + " superinterfaces cannot contain arrays nor primitives");
            }
            interfaces[i] = interfaceType;
        }
        return interfaces;
    }

    int getMajorVersion() {
        assert majorVersion != 0;
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
            if (obj instanceof MethodKey) {
                MethodKey other = (MethodKey) obj;
                return methodName.equals(other.methodName) && signature.equals(other.signature);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

}
