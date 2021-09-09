/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.agent.jdk8.lambda;

import static jdk.internal.org.objectweb.asm.Opcodes.AASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_BRIDGE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ANEWARRAY;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ATHROW;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP;
import static jdk.internal.org.objectweb.asm.Opcodes.GETFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.GETSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static jdk.internal.org.objectweb.asm.Opcodes.IRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.NEW;
import static jdk.internal.org.objectweb.asm.Opcodes.PUTFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.PUTSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;

import java.io.FilePermission;
import java.io.Serializable;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.PropertyPermission;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import sun.invoke.util.BytecodeDescriptor;
import sun.misc.Unsafe;
import sun.security.action.GetPropertyAction;

/**
 * Lambda metafactory implementation which dynamically creates an inner-class-like class per lambda
 * callsite.
 *
 * @see LambdaMetafactory
 */
/* package */ final class InnerClassLambdaMetafactory extends AbstractValidatingLambdaMetafactory {
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    private static final int CLASSFILE_VERSION = 52;
    private static final String METHOD_DESCRIPTOR_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String NAME_CTOR = "<init>";
    private static final String NAME_FACTORY = "get$Lambda";
    private static final String LAMBDA_INSTANCE_FIELD = "LAMBDA_INSTANCE$";

    // Serialization support
    private static final String NAME_SERIALIZED_LAMBDA = "java/lang/invoke/SerializedLambda";
    private static final String NAME_NOT_SERIALIZABLE_EXCEPTION = "java/io/NotSerializableException";
    private static final String DESCR_METHOD_WRITE_REPLACE = "()Ljava/lang/Object;";
    private static final String DESCR_METHOD_WRITE_OBJECT = "(Ljava/io/ObjectOutputStream;)V";
    private static final String DESCR_METHOD_READ_OBJECT = "(Ljava/io/ObjectInputStream;)V";
    private static final String NAME_METHOD_WRITE_REPLACE = "writeReplace";
    private static final String NAME_METHOD_READ_OBJECT = "readObject";
    private static final String NAME_METHOD_WRITE_OBJECT = "writeObject";
    private static final String DESCR_CTOR_SERIALIZED_LAMBDA = MethodType.methodType(void.class,
                    Class.class,
                    String.class, String.class, String.class,
                    int.class, String.class, String.class, String.class,
                    String.class,
                    Object[].class).toMethodDescriptorString();
    private static final String DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION = MethodType.methodType(void.class, String.class).toMethodDescriptorString();
    private static final String[] SER_HOSTILE_EXCEPTIONS = new String[]{NAME_NOT_SERIALIZABLE_EXCEPTION};

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    // For dumping generated classes to disk, for debugging purposes
    private static final ProxyClassesDumper dumper;

    // initialize lambdas eagerly for better startup performance
    private static final boolean eagerlyInitialize = false;

    static {
        final String key = "jdk.internal.lambda.dumpProxyClasses";
        String path = AccessController.doPrivileged(
                        new GetPropertyAction(key), null,
                        new PropertyPermission(key, "read"));
        dumper = (null == path) ? null : ProxyClassesDumper.getInstance(path);
    }

    // See context values in AbstractValidatingLambdaMetafactory
    private final String implMethodClassName;        // Name of type containing implementation "CC"
    private final String implMethodName;             // Name of implementation method "impl"
    private final String implMethodDesc;             // Type descriptor for implementation methods
                                                     // "(I)Ljava/lang/String;"
    private final Class<?> implMethodReturnClass;    // class for implementaion method return type
                                                     // "Ljava/lang/String;"
    private final MethodType constructorType;        // Generated class constructor type "(CC)void"
    private final ClassWriter cw;                    // ASM class writer
    private final String[] argNames;                 // Generated names for the constructor
                                                     // arguments
    private final String[] argDescs;                 // Type descriptors for the constructor
                                                     // arguments
    private final String lambdaClassName;            // Generated name for the generated class
                                                     // "X$$Lambda$1"

    /**
     * General meta-factory constructor, supporting both standard cases and allowing for uncommon
     * options such as serialization or bridging.
     *
     * @param caller Stacked automatically by VM; represents a lookup context with the accessibility
     *            privileges of the caller.
     * @param invokedType Stacked automatically by VM; the signature of the invoked method, which
     *            includes the expected static type of the returned lambda object, and the static
     *            types of the captured arguments for the lambda. In the event that the
     *            implementation method is an instance method, the first argument in the invocation
     *            signature will correspond to the receiver.
     * @param samMethodName Name of the method in the functional interface to which the lambda or
     *            method reference is being converted, represented as a String.
     * @param samMethodType Type of the method in the functional interface to which the lambda or
     *            method reference is being converted, represented as a MethodType.
     * @param implMethod The implementation method which should be called (with suitable adaptation
     *            of argument types, return types, and adjustment for captured arguments) when
     *            methods of the resulting functional interface instance are invoked.
     * @param instantiatedMethodType The signature of the primary functional interface method after
     *            type variables are substituted with their instantiation from the capture site
     * @param isSerializable Should the lambda be made serializable? If set, either the target type
     *            or one of the additional SAM types must extend {@code Serializable}.
     * @param markerInterfaces Additional interfaces which the lambda object should implement.
     * @param additionalBridges Method types for additional signatures to be bridged to the
     *            implementation method
     * @throws LambdaConversionException If any of the meta-factory protocol invariants are violated
     */
    InnerClassLambdaMetafactory(MethodHandles.Lookup caller,
                    MethodType invokedType,
                    String samMethodName,
                    MethodType samMethodType,
                    MethodHandle implMethod,
                    MethodType instantiatedMethodType,
                    boolean isSerializable,
                    Class<?>[] markerInterfaces,
                    MethodType[] additionalBridges)
                    throws LambdaConversionException {
        super(caller, invokedType, samMethodName, samMethodType,
                        implMethod, instantiatedMethodType,
                        isSerializable, markerInterfaces, additionalBridges);
        implMethodClassName = implDefiningClass.getName().replace('.', '/');
        implMethodName = implInfo.getName();
        implMethodDesc = implMethodType.toMethodDescriptorString();
        implMethodReturnClass = (implKind == MethodHandleInfo.REF_newInvokeSpecial)
                        ? implDefiningClass
                        : implMethodType.returnType();
        constructorType = invokedType.changeReturnType(Void.TYPE);
        lambdaClassName = targetClass.getName().replace('.', '/') + "$$Lambda$" + counter.incrementAndGet();
        cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int parameterCount = invokedType.parameterCount();
        if (parameterCount > 0) {
            argNames = new String[parameterCount];
            argDescs = new String[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                argNames[i] = "arg$" + (i + 1);
                argDescs[i] = BytecodeDescriptor.unparse(invokedType.parameterType(i));
            }
        } else {
            argNames = argDescs = EMPTY_STRING_ARRAY;
        }
    }

    /**
     * Build the CallSite. Generate a class file which implements the functional interface, define
     * the class, if there are no parameters create an instance of the class which the CallSite will
     * return, otherwise, generate handles which will call the class' constructor.
     *
     * @return a CallSite, which, when invoked, will return an instance of the functional interface
     * @throws LambdaConversionException If properly formed functional interface is not found
     */
    @Override
    CallSite buildCallSite() throws LambdaConversionException {
        final Class<?> innerClass = spinInnerClass();
        try {
            Field f = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            f.setAccessible(true);

            if (eagerlyInitialize) {
                GraalUnsafeAccess.ensureClassInitialized(innerClass);
            }
            MethodHandles.Lookup implLookup = ((MethodHandles.Lookup) f.get(null));
            MethodHandle lambdaHandle = invokedType.parameterCount() == 0
                            ? implLookup.findStaticGetter(innerClass, LAMBDA_INSTANCE_FIELD, invokedType.returnType())
                            : implLookup.findStatic(innerClass, NAME_FACTORY, invokedType);

            return new ConstantCallSite(lambdaHandle);
        } catch (ReflectiveOperationException e) {
            throw new LambdaConversionException("Exception finding lambda ", e);
        }
    }

    /**
     * Generate a class file which implements the functional interface, define and return the class.
     *
     * @implNote The class that is generated does not include signature information for exceptions
     *           that may be present on the SAM method. This is to reduce classfile size, and is
     *           harmless as checked exceptions are erased anyway, no one will ever compile against
     *           this classfile, and we make no guarantees about the reflective properties of lambda
     *           objects.
     *
     * @return a Class which implements the functional interface
     * @throws LambdaConversionException If properly formed functional interface is not found
     */
    private Class<?> spinInnerClass() throws LambdaConversionException {
        String[] interfaces;
        String samIntf = samBase.getName().replace('.', '/');
        boolean accidentallySerializable = !isSerializable && Serializable.class.isAssignableFrom(samBase);
        if (markerInterfaces.length == 0) {
            interfaces = new String[]{samIntf};
        } else {
            // Assure no duplicate interfaces (ClassFormatError)
            Set<String> itfs = new LinkedHashSet<>(markerInterfaces.length + 1);
            itfs.add(samIntf);
            for (Class<?> markerInterface : markerInterfaces) {
                itfs.add(markerInterface.getName().replace('.', '/'));
                accidentallySerializable |= !isSerializable && Serializable.class.isAssignableFrom(markerInterface);
            }
            interfaces = itfs.toArray(new String[itfs.size()]);
        }

        cw.visit(CLASSFILE_VERSION, ACC_SUPER + ACC_FINAL + ACC_SYNTHETIC,
                        lambdaClassName, null,
                        JAVA_LANG_OBJECT, interfaces);

        // Generate final fields to be filled in by constructor
        for (int i = 0; i < argDescs.length; i++) {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE + ACC_FINAL,
                            argNames[i],
                            argDescs[i],
                            null, null);
            fv.visitEnd();
        }

        generateConstructor();
        if (invokedType.parameterCount() == 0) {
            generateStaticField();
        } else {
            generateFactory();
        }

        // Forward the SAM method
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, samMethodName,
                        samMethodType.toMethodDescriptorString(), null, null);
        mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
        new ForwardingMethodGenerator(mv).generate(samMethodType);

        // Forward the bridges
        if (additionalBridges != null) {
            for (MethodType mt : additionalBridges) {
                mv = cw.visitMethod(ACC_PUBLIC | ACC_BRIDGE, samMethodName,
                                mt.toMethodDescriptorString(), null, null);
                mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
                new ForwardingMethodGenerator(mv).generate(mt);
            }
        }

        if (isSerializable) {
            generateSerializationFriendlyMethods();
        } else if (accidentallySerializable) {
            generateSerializationHostileMethods();
        }

        cw.visitEnd();

        // Define the generated class in this VM.

        final byte[] classBytes = cw.toByteArray();

        // If requested, dump out to a file for debugging purposes
        if (dumper != null) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    dumper.dumpClass(lambdaClassName, classBytes);
                    return null;
                }
            }, null,
                            new FilePermission("<<ALL FILES>>", "read, write"),
                            // createDirectories may need it
                            new PropertyPermission("user.dir", "read"));
        }

        return defineAnonymousClass(classBytes);
    }

    @SuppressWarnings("deprecation") // defineAnonymousClass deprecated since JDK 15
    private Class<?> defineAnonymousClass(final byte[] classBytes) {
        return UNSAFE.defineAnonymousClass(targetClass, classBytes, null);
    }

    /*
     * Generate the factory method for the class
     */
    private void generateFactory() {
        int parameterCount = invokedType.parameterCount();
        if (parameterCount == 0) {
            String lambdaTypeDescriptor = getClassDescriptor(lambdaClassName);
            // Generate a static field with the value
            FieldVisitor fv = cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor, null, null);
            fv.visitEnd();

            // Instantiate the lambda and store it to the field
            MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.visitCode();

            instantiateLambda(parameterCount, clinit);
            clinit.visitFieldInsn(PUTSTATIC, lambdaClassName, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor);

            clinit.visitInsn(RETURN);
            clinit.visitMaxs(-1, -1);
            clinit.visitEnd();

            // The factory now simply returns the value of the static field
            MethodVisitor m = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, NAME_FACTORY, invokedType.toMethodDescriptorString(), null, null);
            m.visitCode();

            m.visitFieldInsn(GETSTATIC, lambdaClassName, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor);
            m.visitInsn(ARETURN);

            m.visitMaxs(-1, -1);
            m.visitEnd();
        } else {
            MethodVisitor m = cw.visitMethod(ACC_PRIVATE | ACC_STATIC, NAME_FACTORY, invokedType.toMethodDescriptorString(), null, null);
            m.visitCode();

            instantiateLambda(parameterCount, m);
            m.visitInsn(ARETURN);

            m.visitMaxs(-1, -1);
            m.visitEnd();
        }
    }

    private void instantiateLambda(int parameterCount, MethodVisitor m) {
        m.visitTypeInsn(NEW, lambdaClassName);
        m.visitInsn(Opcodes.DUP);
        for (int typeIndex = 0, varIndex = 0; typeIndex < parameterCount; typeIndex++) {
            Class<?> argType = invokedType.parameterType(typeIndex);
            m.visitVarInsn(getLoadOpcode(argType), varIndex);
            varIndex += getParameterSize(argType);
        }
        m.visitMethodInsn(INVOKESPECIAL, lambdaClassName, NAME_CTOR, constructorType.toMethodDescriptorString(), false);
    }

    /**
     * Generate the constructor for the class.
     */
    private void generateConstructor() {
        // Generate constructor
        MethodVisitor ctor = cw.visitMethod(ACC_PRIVATE, NAME_CTOR,
                        constructorType.toMethodDescriptorString(), null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, JAVA_LANG_OBJECT, NAME_CTOR,
                        METHOD_DESCRIPTOR_VOID, false);
        int parameterCount = invokedType.parameterCount();
        for (int i = 0, lvIndex = 0; i < parameterCount; i++) {
            ctor.visitVarInsn(ALOAD, 0);
            Class<?> argType = invokedType.parameterType(i);
            ctor.visitVarInsn(getLoadOpcode(argType), lvIndex + 1);
            lvIndex += getParameterSize(argType);
            ctor.visitFieldInsn(PUTFIELD, lambdaClassName, argNames[i], argDescs[i]);
        }
        ctor.visitInsn(RETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        ctor.visitMaxs(-1, -1);
        ctor.visitEnd();
    }

    /**
     * Generate a static field that contains the singleton instance of the lambda.
     */
    private void generateStaticField() {
        String lambdaTypeDescriptor = BytecodeDescriptor.unparse(invokedType.returnType());

        // Generate the static final field that holds the lambda singleton
        FieldVisitor fv = cw.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor, null, null);
        fv.visitEnd();

        // Instantiate the lambda and store it to the static final field
        MethodVisitor clinit = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode();

        instantiateLambda(clinit);
        clinit.visitFieldInsn(PUTSTATIC, lambdaClassName, LAMBDA_INSTANCE_FIELD, lambdaTypeDescriptor);

        clinit.visitInsn(RETURN);
        clinit.visitMaxs(-1, -1);
        clinit.visitEnd();
    }

    private void instantiateLambda(MethodVisitor m) {
        m.visitTypeInsn(NEW, lambdaClassName);
        m.visitInsn(Opcodes.DUP);
        for (int typeIndex = 0, varIndex = 0; typeIndex < invokedType.parameterCount(); typeIndex++) {
            Class<?> argType = invokedType.parameterType(typeIndex);
            m.visitVarInsn(getLoadOpcode(argType), varIndex);
            varIndex += getParameterSize(argType);
        }
        m.visitMethodInsn(INVOKESPECIAL, lambdaClassName, NAME_CTOR, constructorType.toMethodDescriptorString(), false);
    }

    /**
     * Generate a writeReplace method that supports serialization.
     */
    private void generateSerializationFriendlyMethods() {
        TypeConvertingMethodAdapter mv = new TypeConvertingMethodAdapter(
                        cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                                        NAME_METHOD_WRITE_REPLACE, DESCR_METHOD_WRITE_REPLACE,
                                        null, null));

        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_SERIALIZED_LAMBDA);
        mv.visitInsn(DUP);
        mv.visitLdcInsn(Type.getType(targetClass));
        mv.visitLdcInsn(invokedType.returnType().getName().replace('.', '/'));
        mv.visitLdcInsn(samMethodName);
        mv.visitLdcInsn(samMethodType.toMethodDescriptorString());
        mv.visitLdcInsn(implInfo.getReferenceKind());
        mv.visitLdcInsn(implInfo.getDeclaringClass().getName().replace('.', '/'));
        mv.visitLdcInsn(implInfo.getName());
        mv.visitLdcInsn(implInfo.getMethodType().toMethodDescriptorString());
        mv.visitLdcInsn(instantiatedMethodType.toMethodDescriptorString());
        mv.iconst(argDescs.length);
        mv.visitTypeInsn(ANEWARRAY, JAVA_LANG_OBJECT);
        for (int i = 0; i < argDescs.length; i++) {
            mv.visitInsn(DUP);
            mv.iconst(i);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argDescs[i]);
            mv.boxIfTypePrimitive(Type.getType(argDescs[i]));
            mv.visitInsn(AASTORE);
        }
        mv.visitMethodInsn(INVOKESPECIAL, NAME_SERIALIZED_LAMBDA, NAME_CTOR,
                        DESCR_CTOR_SERIALIZED_LAMBDA, false);
        mv.visitInsn(ARETURN);
        // Maxs computed by ClassWriter.COMPUTE_MAXS, these arguments ignored
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * Generate a readObject/writeObject method that is hostile to serialization.
     */
    private void generateSerializationHostileMethods() {
        MethodVisitor mv = cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                        NAME_METHOD_WRITE_OBJECT, DESCR_METHOD_WRITE_OBJECT,
                        null, SER_HOSTILE_EXCEPTIONS);
        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_NOT_SERIALIZABLE_EXCEPTION);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Non-serializable lambda");
        mv.visitMethodInsn(INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                        DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PRIVATE + ACC_FINAL,
                        NAME_METHOD_READ_OBJECT, DESCR_METHOD_READ_OBJECT,
                        null, SER_HOSTILE_EXCEPTIONS);
        mv.visitCode();
        mv.visitTypeInsn(NEW, NAME_NOT_SERIALIZABLE_EXCEPTION);
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Non-serializable lambda");
        mv.visitMethodInsn(INVOKESPECIAL, NAME_NOT_SERIALIZABLE_EXCEPTION, NAME_CTOR,
                        DESCR_CTOR_NOT_SERIALIZABLE_EXCEPTION, false);
        mv.visitInsn(ATHROW);
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    /**
     * This class generates a method body which calls the lambda implementation method, converting
     * arguments, as needed.
     */
    private class ForwardingMethodGenerator extends TypeConvertingMethodAdapter {

        ForwardingMethodGenerator(MethodVisitor mv) {
            super(mv);
        }

        void generate(MethodType methodType) {
            visitCode();

            if (implKind == MethodHandleInfo.REF_newInvokeSpecial) {
                visitTypeInsn(NEW, implMethodClassName);
                visitInsn(DUP);
            }
            for (int i = 0; i < argNames.length; i++) {
                visitVarInsn(ALOAD, 0);
                visitFieldInsn(GETFIELD, lambdaClassName, argNames[i], argDescs[i]);
            }

            convertArgumentTypes(methodType);

            // Invoke the method we want to forward to
            visitMethodInsn(invocationOpcode(), implMethodClassName,
                            implMethodName, implMethodDesc,
                            implDefiningClass.isInterface());

            // Convert the return value (if any) and return it
            // Note: if adapting from non-void to void, the 'return'
            // instruction will pop the unneeded result
            Class<?> samReturnClass = methodType.returnType();
            convertType(implMethodReturnClass, samReturnClass, samReturnClass);
            visitInsn(getReturnOpcode(samReturnClass));
            // Maxs computed by ClassWriter.COMPUTE_MAXS,these arguments ignored
            visitMaxs(-1, -1);
            visitEnd();
        }

        private void convertArgumentTypes(MethodType samType) {
            int lvIndex = 0;
            boolean samIncludesReceiver = implIsInstanceMethod &&
                            invokedType.parameterCount() == 0;
            int samReceiverLength = samIncludesReceiver ? 1 : 0;
            if (samIncludesReceiver) {
                // push receiver
                Class<?> rcvrType = samType.parameterType(0);
                visitVarInsn(getLoadOpcode(rcvrType), lvIndex + 1);
                lvIndex += getParameterSize(rcvrType);
                convertType(rcvrType, implDefiningClass, instantiatedMethodType.parameterType(0));
            }
            int samParametersLength = samType.parameterCount();
            int argOffset = implMethodType.parameterCount() - samParametersLength;
            for (int i = samReceiverLength; i < samParametersLength; i++) {
                Class<?> argType = samType.parameterType(i);
                visitVarInsn(getLoadOpcode(argType), lvIndex + 1);
                lvIndex += getParameterSize(argType);
                convertType(argType, implMethodType.parameterType(argOffset + i), instantiatedMethodType.parameterType(i));
            }
        }

        private int invocationOpcode() throws InternalError {
            switch (implKind) {
                case MethodHandleInfo.REF_invokeStatic:
                    return INVOKESTATIC;
                case MethodHandleInfo.REF_newInvokeSpecial:
                    return INVOKESPECIAL;
                case MethodHandleInfo.REF_invokeVirtual:
                    return INVOKEVIRTUAL;
                case MethodHandleInfo.REF_invokeInterface:
                    return INVOKEINTERFACE;
                case MethodHandleInfo.REF_invokeSpecial:
                    return INVOKESPECIAL;
                default:
                    throw new InternalError("Unexpected invocation kind: " + implKind);
            }
        }
    }

    static int getParameterSize(Class<?> c) {
        if (c == Void.TYPE) {
            return 0;
        } else if (c == Long.TYPE || c == Double.TYPE) {
            return 2;
        }
        return 1;
    }

    static int getLoadOpcode(Class<?> c) {
        if (c == Void.TYPE) {
            throw new InternalError("Unexpected void type of load opcode");
        }
        return ILOAD + getOpcodeOffset(c);
    }

    static int getReturnOpcode(Class<?> c) {
        if (c == Void.TYPE) {
            return RETURN;
        }
        return IRETURN + getOpcodeOffset(c);
    }

    private static int getOpcodeOffset(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == Long.TYPE) {
                return 1;
            } else if (c == Float.TYPE) {
                return 2;
            } else if (c == Double.TYPE) {
                return 3;
            }
            return 0;
        } else {
            return 4;
        }
    }

    private static String getClassDescriptor(String className) {
        StringBuilder b = new StringBuilder();
        b.append('L');
        int len = className.length();
        for (int i = 0; i < len; ++i) {
            char car = className.charAt(i);
            b.append(car == '.' ? '/' : car);
        }
        b.append(';');
        return b.toString();
    }
}
