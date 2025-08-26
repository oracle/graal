/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.interpreter.classfile;

import static com.oracle.svm.interpreter.metadata.Bytecodes.ANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.CHECKCAST;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.GETSTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INSTANCEOF;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEINTERFACE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESPECIAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC2_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.LDC_W;
import static com.oracle.svm.interpreter.metadata.Bytecodes.MULTIANEWARRAY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.NEW;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTFIELD;
import static com.oracle.svm.interpreter.metadata.Bytecodes.PUTSTATIC;

import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ParserConstantPool;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Validation;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ModifiersProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Utility to re-create a .class file representation from a {@link ResolvedJavaType}.
 *
 * <p>
 * .class files and JVMCI data structures are not equivalent, generated .class files may not be
 * loadable on a JVM.
 *
 * <p>
 * <b>Known issues:</b>
 * <ul>
 * <li>Incorrect/hardcoded .class file version</li>
 * <li>Missing attributes</li>
 * <li>INVOKEDYNAMIC (missing BootstrapMethods) is not supported</li>
 * </ul>
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class ClassFile {

    public static final int MAGIC = 0xCAFEBABE;

    // GR-55048: Find .class file version from ResolvedJavaType.
    public static final int MAJOR_VERSION = 55;
    public static final int MINOR_VERSION = 0;

    private final OutStream classFile = new OutStream();

    private final ConstantPoolBuilder poolBuilder;

    public ClassFile(InterpreterUniverse universe, Function<Object, Object> extractConstantValue) {
        this.universe = universe;
        this.extractConstantValue = extractConstantValue;
        this.poolBuilder = new ConstantPoolBuilder(null, MAJOR_VERSION, MINOR_VERSION);
        int invalidEntry = this.poolBuilder.appendInvalid(); // #0
        assert invalidEntry == 0;
    }

    private int utf8(String str) {
        return poolBuilder.appendSymbolicUTF8(utf8Symbol(str));
    }

    private int longConstant(long value) {
        return poolBuilder.appendSymbolicLong(value);
    }

    private int doubleConstant(double value) {
        return poolBuilder.appendSymbolicDouble(value);
    }

    private int intConstant(int value) {
        return poolBuilder.appendSymbolicInteger(value);
    }

    private int floatConstant(float value) {
        return poolBuilder.appendSymbolicFloat(value);
    }

    private int string(String str) {
        return poolBuilder.appendSymbolicString(utf8Symbol(str));
    }

    private int ldcConstant(Object javaConstantOrType) {
        if (javaConstantOrType instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) javaConstantOrType;
            // @formatter:off
            switch (javaConstant.getJavaKind()) {
                case Int    : return intConstant(javaConstant.asInt());
                case Float  : return floatConstant(javaConstant.asFloat());
                case Long   : return longConstant(javaConstant.asLong());
                case Double : return doubleConstant(javaConstant.asDouble());
                case Object : {
                    Object value = extractConstantValue(javaConstant);
                    if (value instanceof String) {
                        return string((String) value);
                    } else if (value instanceof MethodType) {
                        return methodType((MethodType) value);
                    } else {
                        throw VMError.unimplemented("LDC methodHandle constant");
                    }
                }
                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
            // @formatter:on
        } else if (javaConstantOrType instanceof JavaType) {
            return classConstant((JavaType) javaConstantOrType);
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    private int fieldRef(JavaField field) {
        JavaType holderType = field.getDeclaringClass();
        String fieldName = field.getName();
        JavaType fieldType = field.getType();
        return poolBuilder.appendSymbolicField(classNameEntrySymbol(holderType), nameSymbol(fieldName), typeSymbol(fieldType));
    }

    private int classConstant(JavaType type) {
        return poolBuilder.appendSymbolicClass(classNameEntrySymbol(type));
    }

    private static Symbol<Type> typeSymbol(String string) {
        Symbol<Type> symbol = SymbolsSupport.getTypes().getOrCreateValidType(string);
        return Objects.requireNonNull(symbol);
    }

    private static Symbol<Type> typeSymbol(JavaType javaType) {
        return typeSymbol(javaType.getName());
    }

    private static Symbol<Name> classNameEntrySymbol(String internalTypeName) {
        assert Validation.validClassNameEntry(ByteSequence.create(internalTypeName));
        Symbol<Name> classNameEntry = TypeSymbols.toClassNameEntry(typeSymbol(internalTypeName), byteSequence -> SymbolsSupport.getNames().getOrCreate(byteSequence));
        return classNameEntry;
    }

    private static Symbol<Name> classNameEntrySymbol(JavaType javaType) {
        if (javaType.isArray()) {
            // Keep [Ljava/lang/Object;
            return SymbolsSupport.getNames().getOrCreate(ByteSequence.create(javaType.getName()));
        }
        // Allows for hidden classes e.g. Ljava/lang/invoke/LambdaForm$MH.se8db2150; to be "named".
        ByteSequence byteSequence = ByteSequence.create(javaType.toJavaName().replace('.', '/'));
        return SymbolsSupport.getNames().getOrCreate(byteSequence);
    }

    @SuppressWarnings("unused")
    private static Symbol<Name> classNameEntrySymbol(Symbol<Type> typeSymbol) {
        Symbol<Name> classNameEntry = TypeSymbols.toClassNameEntry(typeSymbol, byteSequence -> SymbolsSupport.getNames().getOrCreate(byteSequence));
        assert Validation.validClassNameEntry(classNameEntry);
        return classNameEntry;
    }

    private static Symbol<Name> nameSymbol(String string) {
        Symbol<Name> symbol = SymbolsSupport.getNames().getOrCreate(string);
        return Objects.requireNonNull(symbol);
    }

    private static Symbol<com.oracle.svm.espresso.classfile.descriptors.Signature> signatureSymbol(String string) {
        Symbol<com.oracle.svm.espresso.classfile.descriptors.Signature> symbol = SymbolsSupport.getSignatures().getOrCreateValidSignature(string);
        return Objects.requireNonNull(symbol);
    }

    private static Symbol<? extends ModifiedUTF8> utf8Symbol(String string) {
        Symbol<? extends ModifiedUTF8> symbol = SymbolsSupport.getUtf8().getOrCreateValidUtf8(string);
        return Objects.requireNonNull(symbol);
    }

    private int methodRef(JavaMethod method) {
        JavaType holderType = method.getDeclaringClass();
        String methodName = method.getName();
        String methodSignature = method.getSignature().toMethodDescriptor();
        return poolBuilder.appendSymbolicMethod(false, classNameEntrySymbol(holderType), nameSymbol(methodName), signatureSymbol(methodSignature));
    }

    private int interfaceMethodRef(JavaMethod method) {
        JavaType holderType = method.getDeclaringClass();
        String methodName = method.getName();
        String methodSignature = method.getSignature().toMethodDescriptor();
        return poolBuilder.appendSymbolicMethod(true, classNameEntrySymbol(holderType), nameSymbol(methodName), signatureSymbol(methodSignature));
    }

    @SuppressWarnings("unused")
    private int module(String moduleName) {
        return poolBuilder.appendSymbolicModule(nameSymbol(moduleName));
    }

    @SuppressWarnings("unused")
    private int packageConstant(String packageName) {
        return poolBuilder.appendSymbolicPackage(nameSymbol(packageName));
    }

    private int methodType(MethodType methodType) {
        String descriptor = methodType.descriptorString();
        return poolBuilder.appendSymbolicMethodType(signatureSymbol(descriptor));
    }

    @SuppressWarnings("unused") public static final byte REF_NONE = 0; // null value
    public static final byte REF_getField = 1;
    public static final byte REF_getStatic = 2;
    public static final byte REF_putField = 3;
    public static final byte REF_putStatic = 4;
    public static final byte REF_invokeVirtual = 5;
    public static final byte REF_invokeStatic = 6;
    public static final byte REF_invokeSpecial = 7;
    public static final byte REF_newInvokeSpecial = 8;
    public static final byte REF_invokeInterface = 9;

    @SuppressWarnings("unused") public static final byte REF_LIMIT = 10;

    @SuppressWarnings("unused")
    private int methodHandle(byte referenceKind, JavaField referenceField) {
        VMError.guarantee(referenceKind == REF_getField || referenceKind == REF_getStatic || referenceKind == REF_putField || referenceKind == REF_putStatic);
        return poolBuilder.appendSymbolicMethodHandle(referenceKind,
                        classNameEntrySymbol(referenceField.getDeclaringClass().getName()),
                        nameSymbol(referenceField.getName()),
                        typeSymbol(referenceField.getType().getName()));
    }

    @SuppressWarnings("unused")
    private int methodHandle(byte referenceKind, ResolvedJavaMethod referenceMethod) {
        VMError.guarantee(referenceKind == REF_invokeVirtual || referenceKind == REF_invokeStatic || referenceKind == REF_invokeSpecial || referenceKind == REF_newInvokeSpecial ||
                        referenceKind == REF_invokeInterface);
        boolean interfaceMethod = switch (referenceKind) {
            case REF_invokeVirtual, REF_newInvokeSpecial -> false;
            case REF_invokeStatic, REF_invokeSpecial ->
                // GR-55048: Java version check, interfaceMethodRef
                // allowed after >= 52
                referenceMethod.getDeclaringClass().isInterface();
            case REF_invokeInterface -> true;
            default ->
                throw VMError.shouldNotReachHere("invalid methodHandle ref kind");
        };
        return poolBuilder.appendSymbolicMethodHandle(referenceKind, interfaceMethod,
                        classNameEntrySymbol(referenceMethod.getName()),
                        nameSymbol(referenceMethod.getName()),
                        signatureSymbol(referenceMethod.getSignature().toMethodDescriptor()));
    }

    private final InterpreterUniverse universe;
    private final Function<Object, Object> extractConstantValue;

    private Object extractConstantValue(Object constant) {
        return extractConstantValue.apply(constant);
    }

    //@formatter:off
    private static final Function<Object, Object> EXTRACT_INTERPRETER_CONSTANT_VALUE = (constant) -> {
        if (constant instanceof JavaType) {
            return constant;
        }
        if (constant instanceof PrimitiveConstant primitiveConstant) {
            return primitiveConstant.asBoxedPrimitive();
        }
        if (constant instanceof ReferenceConstant) {
            return ((ReferenceConstant<?>) constant).getReferent();
        }
        throw VMError.shouldNotReachHere("unexpected constant");
    };
    //@formatter:on

    private ResolvedJavaType getSuperclass(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType interpreterResolvedJavaType) {
            Class<?> superclass = interpreterResolvedJavaType.getJavaClass().getSuperclass();
            if (superclass == null) {
                return null;
            }
            return universe.lookupType(superclass);
        } else {
            return type.getSuperclass();
        }
    }

    private ResolvedJavaType[] getInterfaces(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType interpreterResolvedJavaType) {
            Class<?>[] interfaces = interpreterResolvedJavaType.getJavaClass().getInterfaces();
            ResolvedJavaType[] result = new InterpreterResolvedObjectType[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                result[i] = universe.lookupType(interfaces[i]);
            }
            return result;
        } else {
            return type.getInterfaces();
        }
    }

    private ResolvedJavaMethod getClassInitializer(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType) {
            return universe.getAllDeclaredMethods(type)
                            .stream()
                            .filter(ResolvedJavaMethod::isClassInitializer)
                            .findAny()
                            .orElse(null);
        } else {
            return type.getClassInitializer();
        }
    }

    private ResolvedJavaMethod[] getDeclaredConstructors(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType) {
            return universe.getAllDeclaredMethods(type)
                            .stream()
                            .filter(ResolvedJavaMethod::isConstructor)
                            .toArray(InterpreterResolvedJavaMethod[]::new);
        } else {
            return type.getDeclaredConstructors();
        }
    }

    private ResolvedJavaMethod[] getDeclaredMethods(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType) {
            return universe.getAllDeclaredMethods(type)
                            .stream()
                            .filter(method -> !method.isConstructor() && !method.isClassInitializer())
                            .toArray(ResolvedJavaMethod[]::new);
        } else {
            return type.getDeclaredMethods();
        }
    }

    private ResolvedJavaField[] getInstanceFields(ResolvedJavaType type, boolean includeSuperclasses) {
        if (type instanceof InterpreterResolvedJavaType) {
            if (includeSuperclasses) {
                throw VMError.unimplemented("getInstanceFields with includeSuperclasses=true");
            }
            return universe.getAllDeclaredFields(type)
                            .stream()
                            .filter(f -> !f.isStatic())
                            .toArray(ResolvedJavaField[]::new);
        } else {
            return type.getInstanceFields(includeSuperclasses);
        }
    }

    private ResolvedJavaField[] getStaticFields(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType) {
            return universe.getAllDeclaredFields(type)
                            .stream()
                            .filter(ModifiersProvider::isStatic)
                            .toArray(ResolvedJavaField[]::new);
        } else {
            return type.getStaticFields();
        }
    }

    static byte[] dumpResolvedJavaTypeClassFile(InterpreterUniverse universe, ResolvedJavaType type, Function<Object, Object> extractConstantValue) {
        VMError.guarantee(!type.isPrimitive() && !type.isArray());

        ClassFile cf = new ClassFile(universe, extractConstantValue);
        cf.dumpClassFileImpl(type);
        OutStream ensemble = new OutStream();

        // Header
        ensemble.writeInt(MAGIC);
        ensemble.writeU2(MINOR_VERSION);
        ensemble.writeU2(MAJOR_VERSION);

        // The value of the constant_pool_count item is equal to the number of entries in the
        // constant_pool table plus one.
        ParserConstantPool parserConstantPool = cf.poolBuilder.buildParserConstantPool();
        ensemble.writeU2(parserConstantPool.length());
        ensemble.writeBytes(parserConstantPool.toRawBytes());

        // Tail
        ensemble.writeBytes(cf.classFile.toArray());

        return ensemble.toArray();
    }

    public static byte[] dumpInterpreterTypeClassFile(InterpreterUniverse universe, InterpreterResolvedJavaType type) {
        return dumpResolvedJavaTypeClassFile(universe, type, EXTRACT_INTERPRETER_CONSTANT_VALUE);
    }

    void dumpSourceFileAttribute(String sourceFileName) {
        if (sourceFileName == null) {
            return;
        }
        // SourceFile_attribute {
        // u2 attribute_name_index;
        // u4 attribute_length;
        // u2 sourcefile_index;
        // }
        classFile.writeU2(utf8("SourceFile"));
        classFile.writeInt(2);
        classFile.writeU2(utf8(sourceFileName));
    }

    void dumpClassFileImpl(ResolvedJavaType type) {
        // ClassFile {
        // u4 magic;
        // u2 minor_version;
        // u2 major_version;
        // u2 constant_pool_count;
        // cp_info constant_pool[constant_pool_count-1];

        // Constant pool is computed separately during dumping, dumping starts here.

        // u2 access_flags;
        // u2 this_class;
        // u2 super_class;
        // u2 interfaces_count;
        // u2 interfaces[interfaces_count];
        // u2 fields_count;
        // field_info fields[fields_count];
        // u2 methods_count;
        // method_info methods[methods_count];
        // u2 attributes_count;
        // attribute_info attributes[attributes_count];
        // }

        List<ResolvedJavaMethod> allDeclaredMethods = new ArrayList<>();

        // Static initializers are not included for classes already initialized.
        if (getClassInitializer(type) != null) {
            allDeclaredMethods.add(getClassInitializer(type));
        }
        allDeclaredMethods.addAll(Arrays.asList(getDeclaredConstructors(type)));
        allDeclaredMethods.addAll(Arrays.asList(getDeclaredMethods(type)));

        // Write all 1-byte CPIs first in the constant pool.
        processLDC(allDeclaredMethods);
        // processINVOKEDYNAMIC(allDeclaredMethods);

        // u2 access_flags;
        classFile.writeU2(type.getModifiers() & Modifier.classModifiers());

        // u2 this_class;
        classFile.writeU2(classConstant(type)); // GR-55048: Handle hidden classes.

        // u2 super_class;
        if (getSuperclass(type) != null) {
            classFile.writeU2(classConstant(getSuperclass(type)));
        } else {
            classFile.writeU2(0); // no super class
        }

        // u2 interfaces_count;
        classFile.writeU2(getInterfaces(type).length);

        // u2 interfaces[interfaces_count];
        for (JavaType i : getInterfaces(type)) {
            classFile.writeU2(classConstant(i));
        }

        List<ResolvedJavaField> fields = new ArrayList<>();
        fields.addAll(Arrays.asList(getStaticFields(type)));
        fields.addAll(Arrays.asList(getInstanceFields(type, false)));
        // u2 fields_count;
        classFile.writeU2(fields.size());

        // field_info fields[fields_count];
        for (ResolvedJavaField f : fields) {
            dumpFieldInfo(f);
        }

        // u2 methods_count;
        classFile.writeU2(allDeclaredMethods.size());

        // method_info methods[methods_count];
        for (ResolvedJavaMethod m : allDeclaredMethods) {
            dumpMethodInfo(m);
        }

        int attributeCount = 0;
        if (getSourceFileName(type) != null) {
            attributeCount++;
        }

        // u2 attributes_count;
        classFile.writeU2(attributeCount);

        // attribute_info attributes[attributes_count];
        if (getSourceFileName(type) != null) {
            dumpSourceFileAttribute(getSourceFileName(type));
        }

        // SourceFile
        // InnerClasses
        // EnclosingMethod
        // SourceDebugExtension
        // BootstrapMethods
        // Module
        // ModulePackages
        // ModuleMainClass
        // NestHost
        // NestMembers
        // Record
        // PermittedSubclasses
    }

    private static String getSourceFileName(ResolvedJavaType type) {
        if (type instanceof InterpreterResolvedJavaType) {
            return ((InterpreterResolvedObjectType) type).getOriginalType().getSourceFileName();
        } else {
            return type.getSourceFileName();
        }
    }

    final class ConstantWrapper {
        final Object constant;

        ConstantWrapper(Object constant) {
            this.constant = constant;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstantWrapper)) {
                return false;
            }
            ConstantWrapper that = (ConstantWrapper) other;
            Object thisValue = extractConstantValue(this.constant);
            Object thatValue = extractConstantValue(that.constant);
            // Compare types by name.
            if (thisValue instanceof JavaType) {
                return (thatValue instanceof JavaType) &&
                                ((JavaType) thisValue).getName().equals(((JavaType) thatValue).getName());
            }
            return thisValue.equals(thatValue);
        }

        @Override
        public int hashCode() {
            Object value = extractConstantValue(this.constant);
            if (value instanceof JavaType) {
                return ((JavaType) value).getName().hashCode();
            }
            return value.hashCode();
        }
    }

    private void processLDC(List<ResolvedJavaMethod> methods) {
        Set<ConstantWrapper> pending = new HashSet<>();

        for (ResolvedJavaMethod m : methods) {
            if (!m.hasBytecodes()) {
                continue;
            }
            byte[] code = m.getCode();
            if (code == null || code.length == 0) {
                continue;
            }
            for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
                if (BytecodeStream.opcode(code, bci) == LDC) {
                    int originalCPI = BytecodeStream.readCPI(code, bci);
                    if (originalCPI != 0) {
                        Object constant = m.getConstantPool().lookupConstant(originalCPI);
                        if (constant instanceof PrimitiveConstant) {
                            ldcConstant(constant); // push it on the constant pool
                        } else {
                            // Constant can be String, MethodType and MethodHandle.
                            pending.add(new ConstantWrapper(constant));
                        }
                    }
                }
            }
        }
        /*
         * Class, String and MethodType constants are pointers to UTF8 entries. LDC doesn't refer to
         * UTF8 entries directly, but these UTF8 entries may needlessly occupy slots of the 255
         * addressable by LDC, running out of addressable slots for the constants. Constant
         * dependencies are written always before in the constant pool, but in this case UTF8
         * entries must be added after to ensure the constant indices remain addressable by LDC.
         */
        int oldPosition = poolBuilder.getPosition();
        poolBuilder.setPosition(oldPosition + pending.size());

        for (ConstantWrapper wrapper : pending) {
            Object constant = wrapper.constant;
            Object value = extractConstantValue(constant);
            String utf8Entry = null;
            if (value instanceof JavaType javaType) {
                utf8Entry = TypeSymbols.toClassNameEntry(typeSymbol(javaType)).toString();
            } else if (value instanceof String) {
                utf8Entry = (String) value;
            } else if (value instanceof MethodType) {
                MethodType methodType = (MethodType) value;
                utf8Entry = methodType.toMethodDescriptorString();
            } else {
                // MethodHandle is not implemented, never seen one either.
                throw VMError.unimplemented("LDC methodHandle constant");
            }
            VMError.guarantee(utf8Entry != null);
            poolBuilder.appendSymbolicUTF8(utf8Symbol(utf8Entry));
        }
        int finalPosition = poolBuilder.getPosition();

        poolBuilder.setPosition(oldPosition);
        for (ConstantWrapper wrapper : pending) {
            int cpi = ldcConstant(wrapper.constant);
            assert cpi < 256;
        }
        assert poolBuilder.getPosition() == oldPosition + pending.size();
        poolBuilder.setPosition(finalPosition);
    }

    private void dumpFieldInfo(ResolvedJavaField field) {
        int accessFlags = field.getModifiers() & Modifier.fieldModifiers();
        int nameIndex = utf8(field.getName());
        int descriptorIndex = utf8(field.getType().getName());

        // field_info {
        // u2 access_flags;
        // u2 name_index;
        // u2 descriptor_index;
        // u2 attributes_count;
        // attribute_info attributes[attributes_count];
        // }

        classFile.writeU2(accessFlags);
        classFile.writeU2(nameIndex);
        classFile.writeU2(descriptorIndex);

        int attributeCount = 0;
        classFile.writeU2(attributeCount);

        // Field attributes:
        // ConstantValue
        // Synthetic
        // Deprecated
        // Signature
        // RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
        // RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations
    }

    private void dumpMethodInfo(ResolvedJavaMethod method) {
        int accessFlags = method.getModifiers() & Modifier.methodModifiers();
        int nameIndex = utf8(method.getName());
        int descriptorIndex = utf8(method.getSignature().toMethodDescriptor());

        // method_info {
        // u2 access_flags;
        // u2 name_index;
        // u2 descriptor_index;
        // u2 attributes_count;
        // attribute_info attributes[attributes_count];
        // }

        classFile.writeU2(accessFlags);
        classFile.writeU2(nameIndex);
        classFile.writeU2(descriptorIndex);

        int attributeCount = 0;
        if (method.hasBytecodes()) {
            ++attributeCount;
        }

        ResolvedJavaMethod.Parameter[] methodParameters = method.getParameters();
        if (methodParameters != null) {
            ++attributeCount;
        }

        classFile.writeU2(attributeCount);
        if (method.hasBytecodes()) {
            dumpCodeAttribute(method);
        }

        if (methodParameters != null) {
            dumpMethodParameters(methodParameters);
        }

        // Method attributes:
        // Code
        // Exceptions
        // RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations
        // AnnotationDefault
        // MethodParameters
        // Synthetic
        // Deprecated
        // Signature
        // RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations
        // RuntimeVisibleTypeAnnotations, RuntimeInvisibleTypeAnnotations
    }

    private void dumpMethodParameters(ResolvedJavaMethod.Parameter[] methodParameters) {
        if (methodParameters == null) {
            return;
        }

        // MethodParameters_attribute {
        // u2 attribute_name_index;
        // u4 attribute_length;
        // u1 parameters_count;
        // { u2 name_index;
        // u2 access_flags;
        // } parameters[parameters_count];
        // }

        classFile.writeU2(utf8("MethodParameters"));
        int attributeLength = 1 + methodParameters.length * 4;
        classFile.writeInt(attributeLength);

        classFile.writeU1(attributeLength);
        for (ResolvedJavaMethod.Parameter p : methodParameters) {
            classFile.writeU2(utf8(p.getName()));
            classFile.writeU2(p.getModifiers());
        }
    }

    private void dumpLineNumberTable(LineNumberTable lineNumberTable) {
        if (lineNumberTable == null) {
            return;
        }

        // LineNumberTable_attribute {
        // u2 attribute_name_index;
        // u4 attribute_length;
        // u2 line_number_table_length;
        // { u2 start_pc;
        // u2 line_number;
        // } line_number_table[line_number_table_length];
        // }

        classFile.writeU2(utf8("LineNumberTable"));

        int[] lineNumbers = lineNumberTable.getLineNumbers();
        int[] bcis = lineNumberTable.getBcis();

        assert lineNumbers.length == bcis.length;

        int entryCount = lineNumbers.length;

        int attributeLength = 2 + entryCount * 4;
        classFile.writeInt(attributeLength);

        classFile.writeU2(entryCount);
        for (int i = 0; i < entryCount; ++i) {
            classFile.writeU2(bcis[i]);
            classFile.writeU2(lineNumbers[i]);
        }
    }

    private void dumpLocalVariableTable(LocalVariableTable localVariableTable) {
        if (localVariableTable == null) {
            return;
        }

        // LocalVariableTable_attribute {
        // u2 attribute_name_index;
        // u4 attribute_length;
        // u2 local_variable_table_length;
        // { u2 start_pc;
        // u2 length;
        // u2 name_index;
        // u2 descriptor_index;
        // u2 index;
        // } local_variable_table[local_variable_table_length];
        // }

        classFile.writeU2(utf8("LocalVariableTable"));
        Local[] locals = localVariableTable.getLocals();

        int attributeLength = 2 + locals.length * 10;
        classFile.writeInt(attributeLength);

        classFile.writeU2(locals.length);
        for (Local local : locals) {
            classFile.writeU2(local.getStartBCI());
            classFile.writeU2(local.getEndBCI() - local.getStartBCI());
            classFile.writeU2(utf8(local.getName()));
            JavaType type = local.getType();
            if (type == null) {
                classFile.writeU2(0);
            } else {
                classFile.writeU2(utf8(type.getName()));
            }
            classFile.writeU2(local.getSlot());
        }
    }

    private void dumpCodeAttribute(ResolvedJavaMethod method) {
        // Code_attribute {
        // u2 attribute_name_index;
        // u4 attribute_length;
        // u2 max_stack;
        // u2 max_locals;
        // u4 code_length;
        // u1 code[code_length];
        // u2 exception_table_length;
        // { u2 start_pc;
        // u2 end_pc;
        // u2 handler_pc;
        // u2 catch_type;
        // } exception_table[exception_table_length];
        // u2 attributes_count;
        // attribute_info attributes[attributes_count];
        // }

        int startOffset = classFile.getOffset();
        classFile.writeU2(utf8("Code"));
        classFile.writeInt(0xDEADBEEF); // placeholder

        classFile.writeU2(method.getMaxStackSize());
        classFile.writeU2(method.getMaxLocals());

        byte[] code = method.getCode();
        if (code == null) {
            classFile.writeInt(0);
            // empty
        } else {
            classFile.writeInt(code.length);
            classFile.writeBytes(recomputeConstantPoolIndices(method));
        }

        ExceptionHandler[] handlers = method.getExceptionHandlers();
        if (handlers == null || handlers.length == 0) {
            classFile.writeU2(0);
            // empty
        } else {
            classFile.writeU2(handlers.length);
            for (ExceptionHandler eh : handlers) {
                classFile.writeU2(eh.getStartBCI());
                classFile.writeU2(eh.getEndBCI());
                classFile.writeU2(eh.getHandlerBCI());
                classFile.writeU2(eh.catchTypeCPI());
            }
        }

        int attributeCount = 0;
        LineNumberTable lineNumberTable = method.getLineNumberTable();
        if (lineNumberTable != null) {
            ++attributeCount;
        }
        LocalVariableTable localVariableTable = method.getLocalVariableTable();
        if (localVariableTable != null) {
            ++attributeCount;
        }

        classFile.writeU2(attributeCount);

        if (lineNumberTable != null) {
            dumpLineNumberTable(lineNumberTable);
        }
        if (localVariableTable != null) {
            dumpLocalVariableTable(localVariableTable);
        }

        // Code attributes:
        // LineNumberTable
        // LocalVariableTable
        // LocalVariableTypeTable
        // StackMapTable

        int attributeLength = classFile.getOffset() - startOffset - 6;
        classFile.patchAtOffset(startOffset + 2, () -> classFile.writeInt(attributeLength));
    }

    private byte[] recomputeConstantPoolIndices(ResolvedJavaMethod method) {
        byte[] originalCode = method.getCode();
        if (originalCode == null || originalCode.length == 0) {
            return originalCode;
        }

        byte[] code = originalCode.clone();
        ConstantPool originalConstantPool = method.getConstantPool();

        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int bytecode = BytecodeStream.currentBC(code, bci); // also handles wide bytecodes
            // @formatter:off
            switch (bytecode) {
                case CHECKCAST  : // fall-through
                case INSTANCEOF : // fall-through
                case NEW        : // fall-through
                case ANEWARRAY  : // fall-through
                case MULTIANEWARRAY: {
                    int originalCPI = BytecodeStream.readCPI(code, bci);
                    int newCPI = 0;
                    if (originalCPI != 0) {
                        newCPI = classConstant(originalConstantPool.lookupType(originalCPI, bytecode));
                    }
                    BytecodeStream.patchCPI(code, bci, newCPI);
                    break;
                }
                case LDC   : // fall-through
                case LDC_W : // fall-through
                case LDC2_W: {
                    int originalCPI = BytecodeStream.readCPI(code, bci);
                    int newCPI = 0;
                    if (originalCPI != 0) {
                        Object constant = originalConstantPool.lookupConstant(BytecodeStream.readCPI(code, bci));
                        newCPI = ldcConstant(constant);
                    }
                    BytecodeStream.patchCPI(code, bci, newCPI);
                    break;
                }
                case GETSTATIC : // fall-through
                case PUTSTATIC : // fall-through
                case GETFIELD  : // fall-through
                case PUTFIELD: {
                    int originalCPI = BytecodeStream.readCPI(code, bci);
                    int newCPI = 0;
                    if (originalCPI != 0) {
                        newCPI = fieldRef(originalConstantPool.lookupField(originalCPI, method, bytecode));
                    }
                    BytecodeStream.patchCPI(code, bci, newCPI);
                    break;
                }

                case INVOKEVIRTUAL : // fall-through
                case INVOKESPECIAL : // fall-through
                case INVOKESTATIC  : // fall-through
                case INVOKEINTERFACE: {
                    int originalCPI = BytecodeStream.readCPI(code, bci);
                    int newCPI = 0;
                    if (originalCPI != 0) {
                        JavaMethod originalMethod = originalConstantPool.lookupMethod(originalCPI, bytecode);
                        boolean interfaceMethod = switch (bytecode) {
                            case INVOKEVIRTUAL, INVOKESPECIAL -> false;
                            case INVOKESTATIC -> {
                                // GR-55048: Java version check, interfaceMethodRef allowed after >= 52
                                // TODO(peterssen): Need to know if this is an interface method or not.
                                JavaType declaringClass = originalMethod.getDeclaringClass();
                                yield declaringClass instanceof ResolvedJavaType resolvedJavaType && resolvedJavaType.isInterface();
                            }
                            case INVOKEINTERFACE -> true;
                            default ->
                                    throw VMError.shouldNotReachHere("invalid INVOKE opcode");
                        };
                        newCPI = interfaceMethod ? interfaceMethodRef(originalMethod) : methodRef(originalMethod);
                    }
                    BytecodeStream.patchCPI(code, bci, newCPI);
                    break;
                }

                case INVOKEDYNAMIC:
                    // GR-55048: Cannot derive BootstrapMethods attribute and (Invoke)Dynamic CP entry.
                    // The VM already provides the resolved bootstrap method and appendix.
                    // Investigate how to persist the appendix (arbitrary object) on the constant pool.
                    BytecodeStream.patchCPI(code, bci, 0);
                    BytecodeStream.patchAppendixCPI(code, bci, 0);
                    break;
            }
            // @formatter:on
        }

        return code;
    }

}
