/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_INTERFACE;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_RECOGNIZED_CLASS_MODIFIERS;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.runtime.AttributeInfo;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.types.TypeDescriptor;

public class ClassfileParser {

    public static final int MAGIC = 0xCAFEBABE;

    public static final int JAVA_5_VERSION = 49;
    public static final int JAVA_6_VERSION = 50;
    public static final int JAVA_7_VERSION = 51;
    public static final int JAVA_8_VERSION = 52;
    public static final int JAVA_9_VERSION = 53;
    public static final int JAVA_10_VERSION = 54;
    public static final int JAVA_11_VERSION = 55;

    private static final int MAJOR_VERSION_JAVA_MIN = JAVA_6_VERSION;
    private static final int MAJOR_VERSION_JAVA_MAX = JAVA_11_VERSION;

    public static final int STRICTER_ACCESS_CTRL_CHECK_VERSION = JAVA_5_VERSION;
    public static final int STACKMAP_ATTRIBUTE_MAJOR_VERSION = JAVA_6_VERSION;
    public static final int INVOKEDYNAMIC_MAJOR_VERSION = JAVA_7_VERSION;
    public static final int NO_RELAX_ACCESS_CTRL_CHECK_VERSION = JAVA_8_VERSION;
    public static final int DYNAMICCONSTANT_MAJOR_VERSION = JAVA_11_VERSION;

    private final Object classLoader;
    private final ClasspathFile classfile;

    private final String requestedClassName;
    private final EspressoContext context;
    private final ClassfileStream stream;

    private String className;
    private int minorVersion;
    private int majorVersion;

    private ConstantPool pool;

    private int maxBootstrapMethodAttrIndex;
    private boolean needVerify;
    private int accessFlags;
    private Tag badConstantSeen;

    /**
     * The host class for an anonymous class.
     */
    private final Klass hostClass;

    private int superClassIndex;
    private Klass superClass;
    private Klass[] localInterfaces;

    private MethodInfo.Builder[] methods;
    private AttributeInfo[] attributes;
    private FieldInfo.Builder[] fields;
    private TypeDescriptor typeDescriptor;

    public ClassfileParser(Object classLoader, ClasspathFile classpathFile, String requestedClassName, Klass hostClass, EspressoContext context) {
        this.requestedClassName = requestedClassName;
        this.classLoader = classLoader;
        this.className = requestedClassName;
        this.hostClass = hostClass;
        this.context = context;
        this.classfile = classpathFile;
        this.stream = new ClassfileStream(classfile);
    }

    public ClassfileParser(Object classLoader, ClassfileStream stream, String requestedClassName, Klass hostClass, EspressoContext context) {
        this.requestedClassName = requestedClassName;
        this.classLoader = classLoader;
        this.className = requestedClassName;
        this.hostClass = hostClass;
        this.context = context;
        this.classfile = null;
        this.stream = Objects.requireNonNull(stream);
    }

    void handleBadConstant(Tag tag, ClassfileStream s) {
        if (tag == Tag.MODULE || tag == Tag.PACKAGE) {
            if (majorVersion >= JAVA_9_VERSION) {
                s.readU2();
                badConstantSeen = tag;
                return;
            }
        }
        throw stream.classFormatError("Unknown constant tag %d", tag.value);
    }

    void updateMaxBootstrapMethodAttrIndex(int bsmAttrIndex) {
        if (maxBootstrapMethodAttrIndex < bsmAttrIndex) {
            maxBootstrapMethodAttrIndex = bsmAttrIndex;
        }
    }

    void checkInvokeDynamicSupport(Tag tag) {
        if (majorVersion < ClassfileParser.INVOKEDYNAMIC_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag);
        }
    }

    void checkDynamicConstantSupport(Tag tag) {
        if (majorVersion < ClassfileParser.DYNAMICCONSTANT_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag);
        }
    }

    public Klass parseClass() {
        // magic
        int magic = stream.readS4();
        if (magic != MAGIC) {
            throw stream.classFormatError("Incompatible magic value %x in class file %s", magic);
        }

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        if (majorVersion < MAJOR_VERSION_JAVA_MIN || majorVersion > MAJOR_VERSION_JAVA_MAX) {
            throw new UnsupportedClassVersionError("Unsupported class file version: " + majorVersion + "." + minorVersion);
        }

        this.pool = new ConstantPool(context, classLoader, stream, this);

        // JVM_ACC_MODULE is defined in JDK-9 and later.
        if (majorVersion >= JAVA_9_VERSION) {
            accessFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS | Constants.ACC_MODULE);
        } else {
            accessFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS);
        }

        if ((accessFlags & ACC_INTERFACE) != 0 && majorVersion < JAVA_6_VERSION) {
            // Set abstract bit for old class files for backward compatibility
            accessFlags |= ACC_ABSTRACT;
        }

        boolean isModule = (accessFlags & Constants.ACC_MODULE) != 0;
        if (isModule) {
            throw new NoClassDefFoundError(className + " is not a class because access_flag ACC_MODULE is set");
        }

        if (badConstantSeen != null) {
            // Do not throw CFE until after the access_flags are checked because if
            // ACC_MODULE is set in the access flags, then NCDFE must be thrown, not CFE.
            // https://bugs.openjdk.java.net/browse/JDK-8175383
            throw classfile.classFormatError("Unknown constant tag %s", badConstantSeen);
        }

        // This class and superclass
        int thisClassIndex = stream.readU2();

        this.typeDescriptor = pool.classAt(thisClassIndex).getTypeDescriptor(pool, thisClassIndex);

        // Update className which could be null previously
        // to reflect the name in the constant pool
        className = typeDescriptor.toJavaName();

        // Checks if name in class file matches requested name
        if (requestedClassName != null && !requestedClassName.equals(className)) {
            throw new NoClassDefFoundError(className + " (wrong name: " + requestedClassName + ")");
        }

        // if this is an anonymous class fix up its name if it's in the unnamed
        // package. Otherwise, throw IAE if it is in a different package than
        // its host class.
        if (hostClass != null) {
            fixAnonymousClassName();
        }

        this.superClassIndex = parseSuperClass();
        this.localInterfaces = parseInterfaces();

        this.fields = parseFields();
        this.methods = parseMethods();

        this.attributes = parseAttributes();

        if (superClassIndex != 0) {
            this.superClass = pool.classAt(superClassIndex).resolve(pool, superClassIndex);
        } else {
            this.superClass = null;
        }

        Optional<AttributeInfo> optEnclosingMethod = Arrays.stream(attributes).filter(a -> a.getName().equals("EnclosingMethod")).findAny();
        Optional<AttributeInfo> optInnerClasses = Arrays.stream(attributes).filter(a -> a.getName().equals("InnerClasses")).findAny();
        Optional<AttributeInfo> optVisibleAnnotations = Arrays.stream(attributes).filter(a -> a.getName().equals("RuntimeVisibleAnnotations")).findAny();

        return ObjectKlass.create(context, typeDescriptor.toString(), superClass, localInterfaces, methods, fields, accessFlags,
                        (EnclosingMethodAttribute) optEnclosingMethod.orElse(null),
                        (InnerClassesAttribute) optInnerClasses.orElse(null),
                        pool,
                        optVisibleAnnotations.orElse(null));
    }

    private MethodInfo.Builder[] parseMethods() {
        int methodCount = stream.readU2();
        methods = new MethodInfo.Builder[methodCount];
        for (int i = 0; i < methodCount; ++i) {
            methods[i] = parseMethod();
        }
        return methods;
    }

    private static <T> List<T> parseN(int n, Supplier<T> supplier) {
        return Stream.generate(supplier).limit(n).collect(Collectors.toList());
    }

    private MethodInfo.Builder parseMethod() {
        int flags = stream.readU2();
        int nameIndex = stream.readU2();
        String name = pool.utf8At(nameIndex).getValue();

        int descriptorIndex = stream.readU2();
        String value = pool.utf8At(descriptorIndex).getValue();

        AttributeInfo[] attributes = parseAttributes();

        MethodInfo.Builder builder = new MethodInfo.Builder().setName(name).setSignature(context.getSignatureDescriptors().make(value)).setModifiers(flags);

        Optional<AttributeInfo> optCode = Arrays.stream(attributes).filter(a -> a.getName().equals("Code")).findAny();

        if (optCode.isPresent()) {
            CodeAttribute code = (CodeAttribute) optCode.get();
            builder.setMaxLocals(code.getMaxLocals()).setMaxStackSize(code.getMaxStack()).setCode(code.getCode()).setExceptionHandlers(code.getExceptionHandlers());
        }

        return builder;
    }

    private AttributeInfo[] parseAttributes() {
        int count = stream.readU2();
        AttributeInfo[] attributes = new AttributeInfo[count];
        for (int i = 0; i < count; i++) {
            attributes[i] = parseAttribute();
        }
        return attributes;
    }

    private AttributeInfo parseAttribute() {
        int nameIndex = stream.readU2();
        String name = pool.utf8At(nameIndex).getValue();
        switch (name) {
            case "Code":
                return parseCodeAttribute(name);
            case "EnclosingMethod":
                return parseEnclosingMethodAttribute(name);
            case "InnerClasses":
                return parseInnerClasses(name);
            default:
                int length = stream.readS4();
                byte[] info = stream.readByteArray(length);
                return new AttributeInfo(name, info);
        }
    }

    private InnerClassesAttribute parseInnerClasses(String name) {
        int length = stream.readS4();
        int entryCount = stream.readU2();
        InnerClassesAttribute.Entry[] entries = new InnerClassesAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            entries[i] = parseInnerClassEntry();
        }
        return new InnerClassesAttribute(name, entries);
    }

    private InnerClassesAttribute.Entry parseInnerClassEntry() {
        int innerClassIndex = stream.readU2();
        int outerClassIndex = stream.readU2();
        int innerNameIndex = stream.readU2();
        int innerClassAccessFlags = stream.readU2();
        return new InnerClassesAttribute.Entry(innerClassIndex, outerClassIndex, innerNameIndex, innerClassAccessFlags);
    }

    private EnclosingMethodAttribute parseEnclosingMethodAttribute(String name) {
        int length = stream.readS4();
        int classIndex = stream.readU2();
        int methodIndex = stream.readU2();
        return new EnclosingMethodAttribute(name, classIndex, methodIndex);
    }

    private CodeAttribute parseCodeAttribute(String name) {
        int length = stream.readS4();
        int maxStack = stream.readU2();
        int maxLocals = stream.readU2();
        int codeLength = stream.readS4();
        byte[] code = stream.readByteArray(codeLength);
        ExceptionHandler[] entries = parseExceptionHandlerEntries();
        AttributeInfo[] attributes = parseAttributes();
        return new CodeAttribute(name, maxStack, maxLocals, code, entries, attributes);
    }

    private ExceptionHandler[] parseExceptionHandlerEntries() {
        int count = stream.readU2();
        ExceptionHandler[] entries = new ExceptionHandler[count];
        for (int i = 0; i < count; i++) {
            int startPc = stream.readU2();
            int endPc = stream.readU2();
            int handlerPc = stream.readU2();
            int catchTypeIndex = stream.readU2();
            TypeDescriptor catchType = null;
            if (catchTypeIndex != 0) {
                catchType = pool.classAt(catchTypeIndex).getTypeDescriptor(pool, catchTypeIndex);
            }

            entries[i] = new ExceptionHandler(startPc, endPc, handlerPc, catchTypeIndex, catchType);
        }
        return entries;
    }

    private FieldInfo.Builder parseField() {
        int flags = stream.readU2();
        final int nameIndex = stream.readU2();
        final int descriptorIndex = stream.readU2();
        String name = pool.utf8At(nameIndex).getValue();
        TypeDescriptor type = context.getTypeDescriptors().make(pool.utf8At(descriptorIndex).getValue());
        parseAttributes();
        return new FieldInfo.Builder().setName(name).setType(type).setModifiers(flags);
    }

    private FieldInfo.Builder[] parseFields() {
        int fieldCount = stream.readU2();
        FieldInfo.Builder[] fields = new FieldInfo.Builder[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = parseField();
        }
        return fields;
    }

    /**
     * Parses the reference to the super class. Resolving and checking the super class is done after
     * parsing the current class file so that resolution is only attempted if there are no format
     * errors in the current class file.
     */
    private int parseSuperClass() {
        int index = stream.readU2();
        if (index == 0) {
            if (!className.equals("java.lang.Object")) {
                throw classfile.classFormatError("Invalid superclass index 0");
            }
        }
        return index;
    }

    private Klass[] parseInterfaces() {
        int count = stream.readU2();
        Klass[] interfaces = new Klass[count];
        for (int i = 0; i < count; i++) {
            int interfaceIndex = stream.readU2();
            TypeDescriptor interfaceDescriptor = pool.classAt(interfaceIndex).getTypeDescriptor(pool, interfaceIndex);
            Klass interfaceKlass = context.getRegistries().resolve(interfaceDescriptor, classLoader);
            interfaces[i] = interfaceKlass;
        }
        return interfaces;
    }

    private static String getPackageName(String fqn) {
        int slash = fqn.lastIndexOf('/');
        if (slash == -1) {
            int first = 0;
            while (fqn.charAt(first) == '[') {
                first++;
            }
            if (fqn.charAt(first) == 'L') {
                assert fqn.endsWith(";");
                first++;
            }
            int end = fqn.lastIndexOf('/');
            if (end != -1) {
                return fqn.substring(first, end);
            }
        }
        return null;
    }

    /**
     * If the host class and the anonymous class are in the same package then do nothing. If the
     * anonymous class is in the unnamed package then move it to its host's package. If the classes
     * are in different packages then throw an {@link IllegalArgumentException}.
     */
    private void fixAnonymousClassName() {
        int slash = this.typeDescriptor.toJavaName().lastIndexOf('/');
        String hostPackageName = getPackageName(hostClass.getName());
        if (slash == -1) {
            // For an anonymous class that is in the unnamed package, move it to its host class's
            // package by prepending its host class's package name to its class name.
            if (hostPackageName != null) {
                String newClassName = 'L' + hostPackageName + '/' + this.typeDescriptor.toJavaName() + ';';
                this.className = pool.getContext().getTypeDescriptors().make(newClassName).toJavaName();
            }
        } else {
            String packageName = getPackageName(this.className);
            if (!hostPackageName.equals(packageName)) {
                throw new IllegalArgumentException("Host class " + hostClass + " and anonymous class " +
                                this.className + " are in different packages");
            }
        }
    }
}