/*******************************************************************************
 * Copyright (c) 2016, 2019 Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************/

package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.ConstantPool.classFormatError;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ANNOTATION;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_ENUM;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINAL;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_FINALIZER;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_INNER_CLASS;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_INTERFACE;
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
import static com.oracle.truffle.espresso.classfile.Constants.APPEND_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.CHOP_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.FULL_FRAME;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_InitObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_NewObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Object;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_RECOGNIZED_CLASS_MODIFIERS;
import static com.oracle.truffle.espresso.classfile.Constants.RECOGNIZED_INNER_CLASS_MODIFIERS;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_EXTENDED;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_EXTENDED;

import java.lang.reflect.Modifier;
import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.ModifiedUTF8;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public final class ClassfileParser {

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

    @SuppressWarnings("unused") private static final int MAJOR_VERSION_JAVA_MIN = 0;
    @SuppressWarnings("unused") private static final int MAJOR_VERSION_JAVA_MAX = JAVA_14_VERSION;

    public static final int STRICTER_ACCESS_CTRL_CHECK_VERSION = JAVA_1_5_VERSION;
    public static final int STACKMAP_ATTRIBUTE_MAJOR_VERSION = JAVA_6_VERSION;
    public static final int INVOKEDYNAMIC_MAJOR_VERSION = JAVA_7_VERSION;
    public static final int NO_RELAX_ACCESS_CTRL_CHECK_VERSION = JAVA_8_VERSION;
    public static final int DYNAMICCONSTANT_MAJOR_VERSION = JAVA_11_VERSION;

    public static final char JAVA_MIN_SUPPORTED_VERSION = JAVA_1_1_VERSION;
    public static final char JAVA_MAX_SUPPORTED_VERSION = JAVA_8_VERSION;
    public static final char JAVA_MAX_SUPPORTED_MINOR_VERSION = 0;

    private final ClasspathFile classfile;

    private final String requestedClassType;

    private final EspressoContext context;

    private final ClassfileStream stream;

    private final StaticObject[] constantPoolPatches;

    private Symbol<Type> classType;
    private Symbol<Type> classOuterClassType;

    private int minorVersion;
    private int majorVersion;
    private int classFlags;

    private int maxBootstrapMethodAttrIndex = -1;
    private Tag badConstantSeen;

    private ConstantPool pool;

    @SuppressWarnings("unused")
    public Klass getHostClass() {
        return hostClass;
    }

    /**
     * The host class for an anonymous class.
     */
    private final Klass hostClass;

    private ClassfileParser(ClassfileStream stream, String requestedClassType, Klass hostClass, EspressoContext context, StaticObject[] constantPoolPatches) {
        this.requestedClassType = requestedClassType;
        this.hostClass = hostClass;
        this.context = context;
        this.classfile = null;
        this.stream = Objects.requireNonNull(stream);
        this.constantPoolPatches = constantPoolPatches;
    }

    void handleBadConstant(Tag tag, ClassfileStream s) {
        if (tag == Tag.MODULE || tag == Tag.PACKAGE) {
            if (majorVersion >= JAVA_9_VERSION) {
                s.readU2();
                badConstantSeen = tag;
                return;
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
            stream.classFormatError("Class file version does not support constant tag %d", tag.getValue());
        }
    }

    void checkDynamicConstantSupport(Tag tag) {
        if (majorVersion < DYNAMICCONSTANT_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag.getValue());
        }
    }

    public static ParserKlass parse(ClassfileStream stream, String requestedClassName, Klass hostClass, EspressoContext context) {
        return parse(stream, requestedClassName, hostClass, context, null);
    }

    public static ParserKlass parse(ClassfileStream stream, String requestedClassName, Klass hostClass, EspressoContext context, @Host(Object[].class) StaticObject[] constantPoolPatches) {
        return new ClassfileParser(stream, requestedClassName, hostClass, context, constantPoolPatches).parseClass();
    }

    private ParserKlass parseClass() {
        try {
            return parseClassImpl();
        } catch (EspressoException e) {
            throw e;
        } catch (Throwable e) {
            // Warn that some unexpected host-guest exception conversion happened.
            System.err.println("Unexpected host exception " + e + " thrown during class parsing, re-throwing as guest exception.");
            e.printStackTrace();
            throw context.getMeta().throwExWithMessage(e.getClass(), e.getMessage());
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
    private static void verifyVersion(int major, int minor) {
        if ((major >= JAVA_MIN_SUPPORTED_VERSION) &&
                        (major <= JAVA_MAX_SUPPORTED_VERSION) &&
                        ((major != JAVA_MAX_SUPPORTED_VERSION) ||
                                        (minor <= JAVA_MAX_SUPPORTED_MINOR_VERSION))) {
            return;
        }
        throw new UnsupportedClassVersionError("Unsupported major.minor version " + major + "." + minor);
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
            throw classFormatError("Invalid class flags 0x" + Integer.toHexString(flags));
        }
    }

    private ParserKlass parseClassImpl() {
        readMagic();

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        verifyVersion(majorVersion, minorVersion);

        if (constantPoolPatches == null) {
            this.pool = ConstantPool.parse(context.getLanguage(), stream, this);
        } else {
            this.pool = ConstantPool.parse(context.getLanguage(), stream, this, constantPoolPatches, context);
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
            throw new NoClassDefFoundError(classType + " is not a class because access_flag ACC_MODULE is set");
        }

        if (badConstantSeen != null) {
            // Do not throw CFE until after the access_flags are checked because if
            // ACC_MODULE is set in the access flags, then NCDFE must be thrown, not CFE.
            // https://bugs.openjdk.java.net/browse/JDK-8175383
            throw classfile.classFormatError("Unknown constant tag %s", badConstantSeen);
        }

        verifyClassFlags(classFlags, majorVersion);

        // this class
        int thisKlassIndex = stream.readU2();

        Symbol<Name> thisKlassName = pool.classAt(thisKlassIndex).getName(pool);

        final boolean isInterface = Modifier.isInterface(classFlags);

        // TODO(peterssen): Verify class names.

        Symbol<Type> thisKlassType = context.getTypes().fromName(thisKlassName);
        if (Types.isPrimitive(thisKlassType) || Types.isArray(thisKlassType)) {
            throw classFormatError(".this_class cannot be array nor primitive " + classType);
        }

        // Update classType which could be null previously to reflect the name in the constant pool.
        classType = thisKlassType;

        // Checks if name in class file matches requested name
        if (requestedClassType != null && !requestedClassType.equals(classType.toString())) {
            throw new NoClassDefFoundError(classType + " (wrong name: " + requestedClassType + ")");
        }

        Symbol<Type> superKlass = parseSuperKlass();

        if (!Type.Object.equals(classType) && superKlass == null) {
            throw classFormatError("Class " + classType + " must have a superclass");
        }

        if (isInterface && !Type.Object.equals(superKlass)) {
            throw classFormatError("Interface " + classType + " must extend java.lang.Object");
        }

        Symbol<Type>[] superInterfaces = parseInterfaces();

        final ParserField[] fields = parseFields(isInterface);
        final ParserMethod[] methods = parseMethods(isInterface);
        final Attribute[] attributes = parseClassAttributes();

        // Ensure there are no trailing bytes
        stream.checkEndOfFile();

        return new ParserKlass(pool, classFlags, thisKlassName, thisKlassType, superKlass, superInterfaces, methods, fields, attributes, thisKlassIndex);
    }

    private ParserMethod[] parseMethods(boolean isInterface) {
        int methodCount = stream.readU2();
        if (methodCount == 0) {
            return ParserMethod.EMPTY_ARRAY;
        }
        ParserMethod[] methods = new ParserMethod[methodCount];
        for (int i = 0; i < methodCount; ++i) {
            methods[i] = parseMethod(isInterface);
            for (int j = 0; j < i; ++j) {
                if (methods[j].getName().equals(methods[i].getName()) && methods[j].getSignature().equals(methods[i].getSignature())) {
                    throw classFormatError("Duplicate method name and signature: " + methods[j].getName() + " " + methods[j].getSignature());
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
                    valid = (flags & ~(ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_VARARGS | ACC_STRICT | ACC_SYNTHETIC)) == 0;
                }
            }
        }
        if (!valid) {
            throw classFormatError("Invalid method flags 0x" + Integer.toHexString(flags));
        }
    }

    private ParserMethod parseMethod(boolean isInterface) {
        int methodFlags = stream.readU2();
        int nameIndex = stream.readU2();
        int signatureIndex = stream.readU2();

        pool.utf8At(nameIndex).validateMethodName();
        final Symbol<Name> name = pool.symbolAt(nameIndex, "method name");

        int extraFlags = methodFlags;
        boolean isClinit = false;
        boolean isInit = false;
        if (name.equals(Name.CLINIT)) {
            // Class and interface initialization methods (3.9) are called
            // implicitly by the Java virtual machine; the value of their
            // access_flags item is ignored except for the settings of the
            // ACC_STRICT flag.
            methodFlags &= (ACC_STRICT | ACC_STATIC);
            // extraFlags = INITIALIZER | methodFlags;
            isClinit = true;
        } else if (name.equals(Name.INIT)) {
            isInit = true;
        }

        final boolean isStatic = Modifier.isStatic(extraFlags);

        verifyMethodFlags(methodFlags, isInterface, isInit, isClinit, majorVersion);

        pool.utf8At(signatureIndex).validateSignature();
        final Symbol<Signature> signature = Signatures.check(pool.symbolAt(signatureIndex, "method descriptor"));

        if (Signatures.slotsForParameters(context.getSignatures().parsed(signature)) + (isStatic ? 0 : 1) > 255) {
            throw classFormatError("Too many arguments in method signature: " + signature);
        }

        if (name.equals(Name.finalize) && signature.equals(Signature._void) && Modifier.isStatic(methodFlags)) {
            // this class has a finalizer method implementation
            // (this bit will be cleared for java.lang.Object later)
            classFlags |= ACC_FINALIZER;
        }

        int attributeCount = stream.readU2();
        Attribute[] methodAttributes = new Attribute[attributeCount];

        {
            @SuppressWarnings("unused") SignatureAttribute genericSignature = null;
            CodeAttribute codeAttribute = null;
            Attribute checkedExceptions = null;
            Attribute runtimeVisibleAnnotations = null;
            Attribute runtimeVisibleTypeAnnotations = null;
            Attribute runtimeInvisibleTypeAnnotations = null;
            @SuppressWarnings("unused") Attribute runtimeVisibleParameterAnnotations = null;
            @SuppressWarnings("unused") Attribute annotationDefault = null;
            MethodParametersAttribute methodParameters = null;

            for (int i = 0; i < attributeCount; ++i) {
                final int attributeNameIndex = stream.readU2();
                final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
                final int attributeSize = stream.readS4();
                final int startPosition = stream.getPosition();
                if (attributeName.equals(Name.Code)) {
                    if (codeAttribute != null) {
                        throw classFormatError("Duplicate Code attribute");
                    }
                    methodAttributes[i] = codeAttribute = parseCodeAttribute(attributeName);
                } else if (attributeName.equals(Name.Exceptions)) {
                    if (checkedExceptions != null) {
                        throw classFormatError("Duplicate Exceptions attribute");
                    }
                    methodAttributes[i] = checkedExceptions = parseExceptions(attributeName);
                } else if (attributeName.equals(Name.Synthetic)) {
                    methodFlags |= ACC_SYNTHETIC;
                    methodAttributes[i] = checkedExceptions = new Attribute(attributeName, null);
                } else if (majorVersion >= JAVA_1_5_VERSION) {
                    if (attributeName.equals(Name.Signature)) {
                        methodAttributes[i] = genericSignature = parseSignatureAttribute(attributeName);
                    } else if (attributeName.equals(Name.RuntimeVisibleAnnotations)) {
                        assert runtimeVisibleAnnotations == null;
                        methodAttributes[i] = runtimeVisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                    } else if (attributeName.equals(Name.RuntimeVisibleTypeAnnotations)) {
                        if (runtimeVisibleTypeAnnotations != null) {
                            throw classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                        }
                        methodAttributes[i] = runtimeVisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                    } else if (attributeName.equals(Name.RuntimeInvisibleTypeAnnotations)) {
                        if (runtimeInvisibleTypeAnnotations != null) {
                            throw classFormatError("Duplicate RuntimeInvisibleTypeAnnotations attribute");
                        }
                        methodAttributes[i] = runtimeInvisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                    } else if (attributeName.equals(Name.RuntimeVisibleParameterAnnotations)) {
                        methodAttributes[i] = runtimeVisibleParameterAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                    } else if (attributeName.equals(Name.MethodParameters)) {
                        if (methodParameters != null) {
                            throw classFormatError("Duplicate MethodParameters attribute");
                        }
                        methodAttributes[i] = methodParameters = parseMethodParameters(attributeName);
                    } else if (attributeName.equals(Name.AnnotationDefault)) {
                        methodAttributes[i] = annotationDefault = new Attribute(attributeName, stream.readByteArray(attributeSize));
                    } else {
                        // stream.skip(attributeSize);
                        methodAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
                    }
                } else {
                    // stream.skip(attributeSize);
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
        }

        return ParserMethod.create(methodFlags, name, signature, methodAttributes);
    }

    private Attribute[] parseClassAttributes() {
        int attributeCount = stream.readU2();
        if (attributeCount == 0) {
            if (maxBootstrapMethodAttrIndex >= 0) {
                throw classFormatError("BootstrapMethods attribute is missing");
            }
            return Attribute.EMPTY_ARRAY;
        }

        SourceFileAttribute sourceFileName = null;
        @SuppressWarnings("unused") SignatureAttribute genericSignature = null;
        @SuppressWarnings("unused") Attribute runtimeVisibleAnnotations = null;
        Attribute runtimeVisibleTypeAnnotations = null;
        Attribute runtimeInvisibleTypeAnnotations = null;
        EnclosingMethodAttribute enclosingMethod = null;
        BootstrapMethodsAttribute bootstrapMethods = null;
        InnerClassesAttribute innerClasses = null;

        final Attribute[] classAttributes = new Attribute[attributeCount];
        for (int i = 0; i < attributeCount; i++) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (attributeName.equals(Name.SourceFile)) {
                if (sourceFileName != null) {
                    throw classFormatError("Duplicate SourceFile attribute");
                }
                classAttributes[i] = sourceFileName = parseSourceFileAttribute(attributeName);
            } else if (attributeName.equals(Name.Synthetic)) {
                classFlags |= ACC_SYNTHETIC;
                classAttributes[i] = new Attribute(attributeName, null);
            } else if (attributeName.equals(Name.InnerClasses)) {
                if (innerClasses != null) {
                    throw classFormatError("Duplicate InnerClasses attribute");
                }
                classAttributes[i] = innerClasses = parseInnerClasses(attributeName);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (attributeName.equals(Name.Signature)) {
                    classAttributes[i] = genericSignature = parseSignatureAttribute(attributeName);
                } else if (attributeName.equals(Name.RuntimeVisibleAnnotations)) {
                    classAttributes[i] = runtimeVisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                } else if (attributeName.equals(Name.RuntimeVisibleTypeAnnotations)) {
                    if (runtimeVisibleTypeAnnotations != null) {
                        throw classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                    }
                    classAttributes[i] = runtimeVisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                } else if (attributeName.equals(Name.RuntimeInvisibleTypeAnnotations)) {
                    if (runtimeInvisibleTypeAnnotations != null) {
                        throw classFormatError("Duplicate RuntimeInvisibleTypeAnnotations attribute");
                    }
                    classAttributes[i] = runtimeInvisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                } else if (majorVersion >= JAVA_7_VERSION && attributeName.equals(Name.BootstrapMethods)) {
                    if (bootstrapMethods != null) {
                        throw classFormatError("Duplicate BootstrapMethods attribute");
                    }
                    classAttributes[i] = bootstrapMethods = parseBootstrapMethods(attributeName);
                } else if (attributeName.equals(Name.EnclosingMethod)) {
                    if (enclosingMethod != null) {
                        throw classFormatError("Duplicate EnclosingMethod attribute");
                    }
                    classAttributes[i] = enclosingMethod = parseEnclosingMethodAttribute(attributeName);
                } else {
                    // stream.skip(attributeSize);
                    classAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
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
        assert Name.SourceFile.equals(name);
        int sourceFileIndex = stream.readU2();
        return new SourceFileAttribute(name, sourceFileIndex);
    }

    private LineNumberTable parseLineNumberTable(Symbol<Name> name) {
        assert Name.LineNumberTable.equals(name);
        int entryCount = stream.readU2();
        if (entryCount == 0) {
            return LineNumberTable.EMPTY;
        }
        LineNumberTable.Entry[] entries = new LineNumberTable.Entry[entryCount];
        for (int i = 0; i < entryCount; i++) {
            int bci = stream.readU2();
            int lineNumber = stream.readU2();
            entries[i] = new LineNumberTable.Entry(bci, lineNumber);
        }
        return new LineNumberTable(name, entries);
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
            int numBootstrapArguments = stream.readU2();
            char[] bootstrapArguments = new char[numBootstrapArguments];
            for (int j = 0; j < numBootstrapArguments; ++j) {
                bootstrapArguments[j] = (char) stream.readU2();
            }
            entries[i] = new BootstrapMethodsAttribute.Entry((char) bootstrapMethodRef, bootstrapArguments);
        }
        return new BootstrapMethodsAttribute(name, entries);
    }

    private InnerClassesAttribute parseInnerClasses(Symbol<Name> name) {
        assert Name.InnerClasses.equals(name);
        final int entryCount = stream.readU2();
        final InnerClassesAttribute.Entry[] innerClassInfos = new InnerClassesAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            final InnerClassesAttribute.Entry innerClassInfo = parseInnerClassEntry();
            final int innerClassIndex = innerClassInfo.innerClassIndex;
            final int outerClassIndex = innerClassInfo.outerClassIndex;
            innerClassInfos[i] = innerClassInfo;

            // The JVM specification allows a null inner class but don't ask me what it means!
            if (innerClassIndex == 0) {
                continue;
            }

            // If no outer class is specified, then this entry denotes a local or anonymous class
            // that will have an EnclosingMethod attribute instead. That is, these classes are
            // not *immediately* enclosed by another class
            if (outerClassIndex == 0) {
                continue;
            }

            // If this entry refers to the current class, then the current class must be an inner
            // class:
            // it's enclosing class is recorded and it's flags are updated.
            final Symbol<Type> innerClassType = context.getTypes().fromName(pool.classAt(innerClassIndex, "inner class descriptor").getName(pool));
            if (innerClassType.equals(classType)) {
                if (classOuterClassType != null) {
                    throw classFormatError("Duplicate outer class");
                }
                classFlags |= ACC_INNER_CLASS;
                classOuterClassType = context.getTypes().fromName(pool.classAt(outerClassIndex).getName(pool));
                classFlags |= (innerClassInfo.innerClassAccessFlags & RECOGNIZED_INNER_CLASS_MODIFIERS);
            }
            for (int j = 0; j < i; ++j) {
                final InnerClassesAttribute.Entry otherInnerClassInfo = innerClassInfos[j];
                if (otherInnerClassInfo != null) {
                    if (innerClassIndex == otherInnerClassInfo.innerClassIndex && outerClassIndex == otherInnerClassInfo.outerClassIndex) {
                        throw classFormatError("Duplicate entry in InnerClasses attribute");
                    }
                }
            }
        }
        return new InnerClassesAttribute(name, innerClassInfos);
    }

    private InnerClassesAttribute.Entry parseInnerClassEntry() {
        int innerClassIndex = stream.readU2();
        int outerClassIndex = stream.readU2();
        int innerNameIndex = stream.readU2();
        int innerClassAccessFlags = stream.readU2();
        if (innerClassIndex != 0) {
            pool.classAt(innerClassIndex).validate(pool);
        }
        if (outerClassIndex != 0) {
            pool.classAt(outerClassIndex).validate(pool);
        }
        if (innerNameIndex != 0) {
            pool.utf8At(innerNameIndex).validateClassName();
        }
        return new InnerClassesAttribute.Entry(innerClassIndex, outerClassIndex, innerNameIndex, innerClassAccessFlags);
    }

    private EnclosingMethodAttribute parseEnclosingMethodAttribute(Symbol<Name> name) {
        int classIndex = stream.readU2();
        int methodIndex = stream.readU2();
        return new EnclosingMethodAttribute(name, classIndex, methodIndex);
    }

    private StackMapFrame parseStackMapFrame() {
        int frameType = stream.readU1();
        if (frameType < SAME_FRAME_BOUND) {
            return new SameFrame(frameType);
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_BOUND) {
            VerificationTypeInfo stackItem = parseVerificationTypeInfo();
            return new SameLocals1StackItemFrame(frameType, stackItem);
        }
        if (frameType < SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            // [128, 246] is reserved and still unused
            throw classFormatError("Encountered reserved StackMapFrame tag: " + frameType);
        }
        if (frameType == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
            int offsetDelta = stream.readU2();
            VerificationTypeInfo stackItem = parseVerificationTypeInfo();
            return new SameLocals1StackItemFrameExtended(frameType, offsetDelta, stackItem);
        }
        if (frameType < CHOP_BOUND) {
            int offsetDelta = stream.readU2();
            return new ChopFrame(frameType, offsetDelta);
        }
        if (frameType == SAME_FRAME_EXTENDED) {
            int offsetDelta = stream.readU2();
            return new SameFrameExtended(frameType, offsetDelta);
        }
        if (frameType < APPEND_FRAME_BOUND) {
            int offsetDelta = stream.readU2();
            int appendLength = frameType - SAME_FRAME_EXTENDED;
            VerificationTypeInfo[] locals = new VerificationTypeInfo[appendLength];
            for (int i = 0; i < appendLength; i++) {
                locals[i] = parseVerificationTypeInfo();
            }
            return new AppendFrame(frameType, offsetDelta, locals);
        }
        if (frameType == FULL_FRAME) {
            int offsetDelta = stream.readU2();
            int localsLength = stream.readU2();
            VerificationTypeInfo[] locals = new VerificationTypeInfo[localsLength];
            for (int i = 0; i < localsLength; i++) {
                locals[i] = parseVerificationTypeInfo();
            }
            int stackLength = stream.readU2();
            VerificationTypeInfo[] stack = new VerificationTypeInfo[stackLength];
            for (int i = 0; i < stackLength; i++) {
                stack[i] = parseVerificationTypeInfo();
            }
            return new FullFrame(frameType, offsetDelta, locals, stack);
        }
        throw classFormatError("Unrecognized StackMapFrame tag: " + frameType);
    }

    private VerificationTypeInfo parseVerificationTypeInfo() {
        int tag = stream.readU1();
        if (tag < ITEM_InitObject) {
            return new PrimitiveTypeInfo(tag);
        }
        switch (tag) {
            case ITEM_InitObject:
                return new UninitializedThis(tag);
            case ITEM_Object:
                return new ReferenceVariable(tag, stream.readU2());
            case ITEM_NewObject:
                return new UninitializedVariable(tag, stream.readU2());
            default:
                throw classFormatError("Unrecognized verification type info tag: " + tag);
        }
    }

    private StackMapTableAttribute parseStackMapTableAttribute(Symbol<Name> name) {
        int entryCount = stream.readU2();
        StackMapFrame[] entries = new StackMapFrame[entryCount];
        for (int i = 0; i < entryCount; i++) {
            entries[i] = parseStackMapFrame();
        }
        return new StackMapTableAttribute(name, entries);
    }

    private CodeAttribute parseCodeAttribute(Symbol<Name> name) {
        int maxStack = stream.readU2();
        int maxLocals = stream.readU2();

        final int codeLength = stream.readS4();
        if (codeLength <= 0) {
            throw classFormatError("code_length must be > than 0");
        } else if (codeLength > 0xFFFF) {
            throw classFormatError("code_length > than 64 KB");
        }

        byte[] code = stream.readByteArray(codeLength);
        ExceptionHandler[] entries = parseExceptionHandlerEntries();

        int attributeCount = stream.readU2();
        final Attribute[] codeAttributes = new Attribute[attributeCount];

        Attribute runtimeVisibleTypeAnnotations = null;
        Attribute runtimeInvisibleTypeAnnotations = null;
        StackMapTableAttribute stackMapTable = null;

        for (int i = 0; i < attributeCount; i++) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();

            if (attributeName.equals(Name.LineNumberTable)) {
                codeAttributes[i] = parseLineNumberTable(attributeName);
            } else if (attributeName.equals(Name.StackMapTable)) {
                if (stackMapTable != null) {
                    throw classFormatError("Duplicate StackMapTable attribute");
                }
                codeAttributes[i] = stackMapTable = parseStackMapTableAttribute(attributeName);
            } else if (attributeName.equals(Name.RuntimeVisibleTypeAnnotations)) {
                if (runtimeVisibleTypeAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                }
                codeAttributes[i] = runtimeVisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else if (attributeName.equals(Name.RuntimeInvisibleTypeAnnotations)) {
                if (runtimeInvisibleTypeAnnotations != null) {
                    throw classFormatError("Duplicate RuntimeInvisibleTypeAnnotations attribute");
                }
                codeAttributes[i] = runtimeInvisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
            } else {
                // stream.skip(attributeSize);
                codeAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw classFormatError("Invalid attribute length for " + attributeName + " attribute");
            }
        }

        return new CodeAttribute(name, maxStack, maxLocals, code, entries, codeAttributes, majorVersion);

    }

    private ExceptionHandler[] parseExceptionHandlerEntries() {
        int count = stream.readU2();
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
            throw classFormatError(name + ": invalid field flags 0x" + Integer.toHexString(flags));
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
            throw classFormatError("Invalid descriptor: " + rawDescriptor);
        }

        final int attributeCount = stream.readU2();
        final Attribute[] fieldAttributes = new Attribute[attributeCount];

        ConstantValueAttribute constantValue = null;
        @SuppressWarnings("unused") SignatureAttribute genericSignature = null;
        @SuppressWarnings("unused") Attribute runtimeVisibleAnnotations = null;
        Attribute runtimeVisibleTypeAnnotations = null;
        Attribute runtimeInvisibleTypeAnnotations = null;

        for (int i = 0; i < attributeCount; ++i) {
            final int attributeNameIndex = stream.readU2();
            final Symbol<Name> attributeName = pool.symbolAt(attributeNameIndex, "attribute name");
            final int attributeSize = stream.readS4();
            final int startPosition = stream.getPosition();
            if (isStatic && attributeName.equals(Name.ConstantValue)) {
                if (constantValue != null) {
                    throw classFormatError("Duplicate ConstantValue attribute");
                }
                fieldAttributes[i] = constantValue = new ConstantValueAttribute(stream.readU2());
                if (constantValue.getConstantValueIndex() == 0) {
                    throw classFormatError("Invalid ConstantValue index");
                }
            } else if (attributeName.equals(Name.Synthetic)) {
                fieldFlags |= ACC_SYNTHETIC;
                fieldAttributes[i] = new Attribute(attributeName, null);
            } else if (majorVersion >= JAVA_1_5_VERSION) {
                if (attributeName.equals(Name.Signature)) {
                    fieldAttributes[i] = genericSignature = parseSignatureAttribute(attributeName);
                } else if (attributeName.equals(Name.RuntimeVisibleAnnotations)) {
                    fieldAttributes[i] = runtimeVisibleAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                } else if (attributeName.equals(Name.RuntimeVisibleTypeAnnotations)) {
                    if (runtimeVisibleTypeAnnotations != null) {
                        throw classFormatError("Duplicate RuntimeVisibleTypeAnnotations attribute");
                    }
                    fieldAttributes[i] = runtimeVisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                } else if (attributeName.equals(Name.RuntimeInvisibleTypeAnnotations)) {
                    if (runtimeInvisibleTypeAnnotations != null) {
                        throw classFormatError("Duplicate RuntimeInvisibleTypeAnnotations attribute");
                    }
                    fieldAttributes[i] = runtimeInvisibleTypeAnnotations = new Attribute(attributeName, stream.readByteArray(attributeSize));
                } else {
                    // stream.skip(attributeSize);
                    fieldAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
                }
            } else {
                // stream.skip(attributeSize);
                fieldAttributes[i] = new Attribute(attributeName, stream.readByteArray(attributeSize));
            }

            if (attributeSize != stream.getPosition() - startPosition) {
                throw classFormatError("Invalid attribute_length for " + attributeName + " attribute");
            }
        }

        final JavaKind kind = Types.getJavaKind(descriptor);
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
                    valid = (tag == Tag.STRING) && descriptor.equals(Type.String);
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
            if (!classType.equals(Type.Object)) {
                throw classFormatError("Invalid superclass index 0");
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
                throw classFormatError(classType + " contains invalid superinterface name: " + interfaceName);
            }
            if (Types.isPrimitive(interfaceType) || Types.isArray(interfaceType)) {
                throw classFormatError(classType + " superinterfaces cannot contain arrays nor primitives");
            }
            interfaces[i] = interfaceType;
        }
        return interfaces;
    }

    int getMajorVersion() {
        assert majorVersion != 0;
        return majorVersion;
    }
}