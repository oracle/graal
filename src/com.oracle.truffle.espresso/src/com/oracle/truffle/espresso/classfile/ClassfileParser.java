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
import static com.oracle.truffle.espresso.classfile.Constants.ACC_SYNTHETIC;
import static com.oracle.truffle.espresso.classfile.Constants.APPEND_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.CHOP_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.FULL_FRAME;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_InitObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_NewObject;
import static com.oracle.truffle.espresso.classfile.Constants.ITEM_Object;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_RECOGNIZED_CLASS_MODIFIERS;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_FRAME_EXTENDED;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_BOUND;
import static com.oracle.truffle.espresso.classfile.Constants.SAME_LOCALS_1_STACK_ITEM_EXTENDED;

import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class ClassfileParser {

    public static final int MAGIC = 0xCAFEBABE;

    public static final int JAVA_4_VERSION = 48;
    public static final int JAVA_5_VERSION = 49;
    public static final int JAVA_6_VERSION = 50;
    public static final int JAVA_7_VERSION = 51;
    public static final int JAVA_8_VERSION = 52;
    public static final int JAVA_9_VERSION = 53;
    public static final int JAVA_10_VERSION = 54;
    public static final int JAVA_11_VERSION = 55;

    private static final int MAJOR_VERSION_JAVA_MIN = 0;
    private static final int MAJOR_VERSION_JAVA_MAX = JAVA_11_VERSION;

    public static final int STRICTER_ACCESS_CTRL_CHECK_VERSION = JAVA_5_VERSION;
    public static final int STACKMAP_ATTRIBUTE_MAJOR_VERSION = JAVA_6_VERSION;
    public static final int INVOKEDYNAMIC_MAJOR_VERSION = JAVA_7_VERSION;
    public static final int NO_RELAX_ACCESS_CTRL_CHECK_VERSION = JAVA_8_VERSION;
    public static final int DYNAMICCONSTANT_MAJOR_VERSION = JAVA_11_VERSION;

    private final ClasspathFile classfile;

    private final String requestedClassName;

    private final EspressoContext context;

    private final ClassfileStream stream;

    private final StaticObject[] constantPoolPatches;

    private String className;
    private int minorVersion;
    private int majorVersion;

    private int maxBootstrapMethodAttrIndex;
    private Tag badConstantSeen;

    private ConstantPool pool;
    private int thisKlassIndex;

    @SuppressWarnings("unused")
    public Klass getHostClass() {
        return hostClass;
    }

    // /**
    // * The host class for an anonymous class.
    // */
    private final Klass hostClass;

    @SuppressWarnings("unused")
    private ClassfileParser(ClasspathFile classpathFile, String requestedClassName, @SuppressWarnings("unused") Klass hostClass, EspressoContext context) {
        this.requestedClassName = requestedClassName;
        this.className = requestedClassName;
        this.hostClass = hostClass;
        this.context = context;
        this.classfile = classpathFile;
        this.stream = new ClassfileStream(classfile);
        this.constantPoolPatches = null;
    }

    private ClassfileParser(ClassfileStream stream, String requestedClassName, @SuppressWarnings("unused") Klass hostClass, EspressoContext context) {
        this.requestedClassName = requestedClassName;
        this.className = requestedClassName;
        this.hostClass = hostClass;
        this.context = context;
        this.classfile = null;
        this.stream = Objects.requireNonNull(stream);
        this.constantPoolPatches = null;
    }

    public ClassfileParser(ClassfileStream stream, String requestedClassName, @SuppressWarnings("unused") Klass hostClass, EspressoContext context, StaticObject[] constantPoolPatches) {
        this.requestedClassName = requestedClassName;
        this.className = requestedClassName;
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
        if (majorVersion < ClassfileParser.INVOKEDYNAMIC_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag.getValue());
        }
    }

    void checkDynamicConstantSupport(Tag tag) {
        if (majorVersion < ClassfileParser.DYNAMICCONSTANT_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag.getValue());
        }
    }

    boolean useStackMapTables() {
        return majorVersion >= JAVA_6_VERSION;
    }

    public static ParserKlass parse(ClassfileStream stream, String requestedClassName, Klass hostClass, EspressoContext context) {
        return new ClassfileParser(stream, requestedClassName, hostClass, context).parseClass();
    }

    public static ParserKlass parse(ClasspathFile classpathFile, String requestedClassName, Klass hostClass, EspressoContext context) {
        return parse(new ClassfileStream(classpathFile), requestedClassName, hostClass, context);
    }

    public ParserKlass parseClass() {
        try {
            return parseClassImpl();
        } catch (EspressoException e) {
            throw e;
        } catch (ClassFormatError | VerifyError e) {
            // These exceptions are expected.
            throw context.getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        } catch (Throwable e) {
            // Warn that some unexpected host-guest exception conversion happened.
            System.err.println("Unexpected host exception " + e + " thrown during class parsing, re-throwing as guest exception.");
            e.printStackTrace();
            throw context.getMeta().throwExWithMessage(e.getClass(), e.getMessage());
        }
    }

    public ParserKlass parseClassImpl() {
        // magic
        int magic = stream.readS4();
        if (magic != MAGIC) {
            throw stream.classFormatError("Incompatible magic value %x in class file %s", magic, classfile);
        }

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        if (majorVersion < MAJOR_VERSION_JAVA_MIN || majorVersion > MAJOR_VERSION_JAVA_MAX) {
            throw new UnsupportedClassVersionError("Unsupported class file version: " + majorVersion + "." + minorVersion);
        }

        if (constantPoolPatches == null) {
            this.pool = ConstantPool.parse(context.getLanguage(), stream, this);
        } else {
            this.pool = ConstantPool.parse(context.getLanguage(), stream, this, constantPoolPatches, context);
        }

        // JVM_ACC_MODULE is defined in JDK-9 and later.
        int accessFlags;
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
        thisKlassIndex = stream.readU2();

        // this.typeDescriptor = pool.classAt(thisKlassIndex).getName(pool);

        // Update className which could be null previously
        // to reflect the name in the constant pool
        className = "L" + pool.classAt(thisKlassIndex).getName(pool).toString() + ";";

        // Checks if name in class file matches requested name
        if (requestedClassName != null && !requestedClassName.equals(className)) {
            throw new NoClassDefFoundError(className + " (wrong name: " + requestedClassName + ")");
        }

        Symbol<Name> thisKlassName = pool.classAt(thisKlassIndex).getName(pool);
        Symbol<Type> thisKlassType = context.getTypes().fromName(thisKlassName);

        Symbol<Type> superKlass = parseSuperKlass();
        Symbol<Type>[] superInterfaces = parseInterfaces();

        ParserField[] fields = parseFields();
        ParserMethod[] methods = parseMethods();
        Attribute[] attributes = parseAttributes();

        return new ParserKlass(pool, accessFlags, thisKlassName, thisKlassType, superKlass, superInterfaces, methods, fields, attributes);
    }

    private ParserMethod[] parseMethods() {
        int methodCount = stream.readU2();
        if (methodCount == 0) {
            return ParserMethod.EMPTY_ARRAY;
        }
        ParserMethod[] methods = new ParserMethod[methodCount];
        for (int i = 0; i < methodCount; ++i) {
            methods[i] = parseMethod();
        }
        return methods;
    }

    private ParserMethod parseMethod() {
        int flags = stream.readU2();
        int nameIndex = stream.readU2();
        int signatureIndex = stream.readU2();
        Attribute[] methodAttributes = parseAttributes();
        for (Attribute attr : methodAttributes) {
            if (attr.getName().equals(Name.Synthetic)) {
                flags |= ACC_SYNTHETIC;
                break;
            }
        }
        return new ParserMethod(flags, nameIndex, signatureIndex, methodAttributes);
    }

    private Attribute[] parseAttributes() {
        int attributeCount = stream.readU2();
        if (attributeCount == 0) {
            return Attribute.EMPTY_ARRAY;
        }
        Attribute[] attrs = new Attribute[attributeCount];
        for (int i = 0; i < attributeCount; i++) {
            attrs[i] = parseAttribute();
        }
        return attrs;
    }

    private Attribute parseAttribute() {
        int nameIndex = stream.readU2();
        Symbol<Name> name = pool.utf8At(nameIndex);
        if (CodeAttribute.NAME.equals(name)) {
            return parseCodeAttribute(name);
        }
        if (EnclosingMethodAttribute.NAME.equals(name)) {
            return parseEnclosingMethodAttribute(name);
        }
        if (InnerClassesAttribute.NAME.equals(name)) {
            return parseInnerClasses(name);
        }
        if (ExceptionsAttribute.NAME.equals(name)) {
            return parseExceptions(name);
        }
        if (BootstrapMethodsAttribute.NAME.equals(name)) {
            return parseBootstrapMethods(name);
        }
        if (ConstantValueAttribute.NAME.equals(name)) {
            return parseConstantValue(name);
        }
        if (MethodParametersAttribute.NAME.equals(name)) {
            return parseMethodParameters(name);
        }
        if (SignatureAttribute.NAME.equals(name)) {
            return parseSignatureAttribute(name);
        }
        if (LineNumberTable.NAME.equals(name)) {
            return parseLineNumberTable(name);
        }
        if (SourceFileAttribute.NAME.equals(name)) {
            return parseSourceFileAttribute(name);
        }
        if (StackMapTableAttribute.NAME.equals(name)) {
            return parseStackMapTableAttribute(name);
        }
        int length = stream.readS4();
        byte[] data = stream.readByteArray(length);
        return new Attribute(name, data);
    }

    private SourceFileAttribute parseSourceFileAttribute(Symbol<Name> name) {
        assert Name.SourceFile.equals(name);
        /* int length = */ stream.readS4();
        int sourceFileIndex = stream.readU2();
        return new SourceFileAttribute(name, sourceFileIndex);
    }

    private LineNumberTable parseLineNumberTable(Symbol<Name> name) {
        assert Name.LineNumberTable.equals(name);
        /* int length = */ stream.readS4();
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
        /* int length = */ stream.readS4();
        int signatureIndex = stream.readU2();
        return new SignatureAttribute(name, signatureIndex);
    }

    private MethodParametersAttribute parseMethodParameters(Symbol<Name> name) {
        /* int length = */ stream.readS4();
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

    private Attribute parseConstantValue(Symbol<Name> name) {
        assert Name.ConstantValue.equals(name);
        /* int length = */ stream.readS4();
        int constantValueIndex = stream.readU2();
        return new ConstantValueAttribute(constantValueIndex);
    }

    private ExceptionsAttribute parseExceptions(Symbol<Name> name) {
        assert Name.Exceptions.equals(name);
        /* int length = */ stream.readS4();
        int entryCount = stream.readU2();
        int[] entries = new int[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int index = stream.readU2();
            entries[i] = index;
        }
        return new ExceptionsAttribute(name, entries);
    }

    private BootstrapMethodsAttribute parseBootstrapMethods(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int entryCount = stream.readU2();
        BootstrapMethodsAttribute.Entry[] entries = new BootstrapMethodsAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int bootstrapMethodRef = stream.readU2();
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
        /* int length = */ stream.readS4();
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

    private EnclosingMethodAttribute parseEnclosingMethodAttribute(Symbol<Name> name) {
        /* int length = */ stream.readS4();
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
            throw new ClassFormatError("Encountered reserved StackMapFrame tag: " + frameType);
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
        throw new ClassFormatError("Unrecognized StackMapFrame tag: " + frameType);
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
                throw new ClassFormatError("Unrecognized verification type info tag: " + tag);
        }
    }

    private StackMapTableAttribute parseStackMapTableAttribute(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int entryCount = stream.readU2();
        StackMapFrame[] entries = new StackMapFrame[entryCount];
        for (int i = 0; i < entryCount; i++) {
            entries[i] = parseStackMapFrame();
        }
        return new StackMapTableAttribute(name, entries);
    }

    private CodeAttribute parseCodeAttribute(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int maxStack = stream.readU2();
        int maxLocals = stream.readU2();
        int codeLength = stream.readS4();
        byte[] code = stream.readByteArray(codeLength);
        ExceptionHandler[] entries = parseExceptionHandlerEntries();
        Attribute[] codeAttributes = parseAttributes();
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

    private ParserField parseField() {
        int flags = stream.readU2();
        int nameIndex = stream.readU2();
        int typeIndex = stream.readU2();
        Attribute[] fieldAttributes = parseAttributes();
        for (Attribute attr : fieldAttributes) {
            if (attr.getName().equals(Name.Synthetic)) {
                flags |= ACC_SYNTHETIC;
                break;
            }
        }
        return new ParserField(flags, pool.utf8At(nameIndex), pool.utf8At(typeIndex), typeIndex, fieldAttributes);
    }

    private ParserField[] parseFields() {
        int fieldCount = stream.readU2();
        if (fieldCount == 0) {
            return ParserField.EMPTY_ARRAY;
        }
        ParserField[] fields = new ParserField[fieldCount];
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
    private Symbol<Type> parseSuperKlass() {
        int index = stream.readU2();
        if (index == 0) {
            if (!className.equals("Ljava/lang/Object;")) {
                throw classfile.classFormatError("Invalid superclass index 0");
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
            interfaces[i] = context.getTypes().fromName(pool.classAt(interfaceIndex).getName(pool));
        }
        return interfaces;
    }

    public int getThisKlassIndex() {
        return thisKlassIndex;
    }
}