/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.Utils;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.ExceptionsAttribute;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.LineNumberTable;
import com.oracle.truffle.espresso.meta.LocalVariableTable;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicReflectionRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicRootNode;
import com.oracle.truffle.espresso.nodes.JniNativeNode;
import com.oracle.truffle.espresso.nodes.NativeRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.espresso.vm.VM;
import com.oracle.truffle.nfi.types.NativeSimpleType;

import java.lang.reflect.Modifier;

import static com.oracle.truffle.espresso.meta.Meta.meta;

public final class MethodInfo implements ModifiersProvider {

    public final static MethodInfo[] EMPTY_ARRAY = new MethodInfo[0];

    private final ObjectKlass declaringClass;
    private final String name;
    private final SignatureDescriptor signature;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final byte[] code;

    private final int maxStackSize;
    private final int maxLocals;
    private final ExceptionHandler[] exceptionHandlers;
    private final LineNumberTable lineNumberTable;
    private final LocalVariableTable localVariableTable;
    private final int modifiers;
    private final ExceptionsAttribute exceptionsAttribute;

    @CompilerDirectives.CompilationFinal private boolean intrinsified = false;

    @CompilerDirectives.CompilationFinal private CallTarget callTarget;
    @CompilerDirectives.CompilationFinal private Klass returnType;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private Klass[] parameterTypes;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private Klass[] checkedExceptions;

    MethodInfo(ObjectKlass declaringClass, String name, SignatureDescriptor signature,
                    byte[] code, int maxStackSize, int maxLocals, int modifiers,
                    ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable, ExceptionsAttribute exceptionsAttribute) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.signature = signature;
        this.code = code;
        this.maxStackSize = maxStackSize;
        this.maxLocals = maxLocals;
        this.modifiers = modifiers;
        this.exceptionHandlers = exceptionHandlers;
        this.lineNumberTable = lineNumberTable;
        this.localVariableTable = localVariableTable;
        this.exceptionsAttribute = exceptionsAttribute;
    }

    public EspressoContext getContext() {
        return declaringClass.getContext();
    }

    public byte[] getCode() {
        return code;
    }

    public int getCodeSize() {
        return code != null ? code.length : 0;
    }

    public String getName() {
        return name;
    }

    public ObjectKlass getDeclaringClass() {
        return declaringClass;
    }

    public SignatureDescriptor getSignature() {
        return signature;
    }

    public int getMaxLocals() {
        return maxLocals;
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlers;
    }

    public ConstantPool getConstantPool() {
        return declaringClass.getConstantPool();
    }

    private static String buildJniNativeSignature(Meta.Method method) {
        // Prepend JNIEnv*.
        StringBuilder sb = new StringBuilder("(").append(NativeSimpleType.SINT64);
        SignatureDescriptor signature = method.rawMethod().getSignature();

        // Receiver for instance methods, class for static methods.
        sb.append(", ").append(NativeSimpleType.NULLABLE);

        int argCount = signature.getParameterCount(false);
        for (int i = 0; i < argCount; ++i) {
            sb.append(", ").append(Utils.kindToType(signature.getParameterKind(i), true));
        }

        sb.append("): ").append(Utils.kindToType(signature.resultKind(), false));

        return sb.toString();
    }

    private static TruffleObject bind(TruffleObject library, Meta.Method m, String mangledName) throws UnknownIdentifierException {
        String signature = buildJniNativeSignature(m);
        return NativeLibrary.lookupAndBind(library, mangledName, signature);
    }

    private static TruffleObject bind(TruffleObject symbol, Meta.Method m) {
        String signature = buildJniNativeSignature(m);
        return NativeLibrary.bind(symbol, signature);
    }

    @CompilerDirectives.TruffleBoundary
    public CallTarget getCallTarget() {
        // TODO(peterssen): Make lazy call target thread-safe.
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();

            // TODO(peterssen): Rethink method substitution logic.
            RootNode redirectedMethod = getContext().getInterpreterToVM().getSubstitution(this);
            if (redirectedMethod != null) {
                if (redirectedMethod instanceof IntrinsicReflectionRootNode) {
                    ((IntrinsicReflectionRootNode) redirectedMethod).setOriginalMethod(Meta.meta(this));
                } else if (redirectedMethod instanceof IntrinsicRootNode) {
                    ((IntrinsicRootNode) redirectedMethod).setOriginalMethod(Meta.meta(this));
                } else if (redirectedMethod instanceof NativeRootNode) {
                    ((NativeRootNode) redirectedMethod).setOriginalMethod(Meta.meta(this));
                }
                intrinsified = true;
                callTarget = Truffle.getRuntime().createCallTarget(redirectedMethod);
            } else {
                if (this.isNative()) {
                    // Bind native method.
                    // System.err.println("Linking native method: " +
                    // meta(this).getDeclaringClass().getName() + "#" + getName() + " " +
                    // getSignature());
                    Meta meta = getContext().getMeta();

                    // If the loader is null we have a system class, so we attempt a lookup in
                    // the native Java library.
                    if (StaticObject.isNull(getDeclaringClass().getClassLoader())) {
                        // Look in libjava
                        VM vm = EspressoLanguage.getCurrentContext().getVM();
                        for (boolean withSignature : new boolean[]{false, true}) {
                            String mangledName = Mangle.mangleMethod(meta(this), withSignature);

                            try {
                                TruffleObject nativeMethod = bind(vm.getJavaLibrary(), meta(this), mangledName);
                                callTarget = Truffle.getRuntime().createCallTarget(new JniNativeNode(getContext().getLanguage(), nativeMethod, meta(this)));
                                return callTarget;
                            } catch (UnknownIdentifierException e) {
                                // native method not found in libjava, safe to ignore
                            }
                        }
                    }

                    Meta.Method.WithInstance findNative = meta.knownKlass(ClassLoader.class).staticMethod("findNative", long.class, ClassLoader.class, String.class);

                    // Lookup the short name first, otherwise lookup the long name (with signature).
                    callTarget = lookupJniCallTarget(findNative, false);
                    if (callTarget == null) {
                        callTarget = lookupJniCallTarget(findNative, true);
                    }

                    // TODO(peterssen): Search JNI methods with OS prefix/suffix
                    // (print_jni_name_suffix_on ...)

                    if (callTarget == null) {
                        System.err.println("Failed to link native method: " + meta(this).getDeclaringClass().getName() + "#" + getName() + " " + getSignature());
                        throw meta.throwEx(UnsatisfiedLinkError.class);
                    }
                } else {
                    callTarget = Truffle.getRuntime().createCallTarget(new EspressoRootNode(getContext().getLanguage(), this, getContext().getInterpreterToVM()));
                }
            }
        }

        return callTarget;
    }

    private CallTarget lookupJniCallTarget(Meta.Method.WithInstance findNative, boolean fullSignature) {
        String mangledName = Mangle.mangleMethod(meta(this), fullSignature);
        long handle = (long) findNative.invoke(getDeclaringClass().getClassLoader(), mangledName);
        if (handle == 0) { // not found
            return null;
        }
        TruffleObject symbol = EspressoLanguage.getCurrentContext().getVM().getFunction(handle);
        TruffleObject nativeMethod = bind(symbol, meta(this));
        return Truffle.getRuntime().createCallTarget(new JniNativeNode(getContext().getLanguage(), nativeMethod, meta(this)));
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isConstructor() {
        assert signature.resultKind() == JavaKind.Void;
        assert !isStatic();
        return "<init>".equals(getName());
    }

    public boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    public Klass[] getParameterTypes() {
        if (parameterTypes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            int argCount = getSignature().getParameterCount(false);
            parameterTypes = new Klass[argCount];
            for (int i = 0; i < argCount; ++i) {
                parameterTypes[i] = getContext().getRegistries().resolve(getSignature().getParameterType(i), getClassLoader());
            }
        }
        return parameterTypes;
    }

    public Klass[] getCheckedExceptions() {
        if (checkedExceptions == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (exceptionsAttribute == null) {
                checkedExceptions = Klass.EMPTY_ARRAY;
                return checkedExceptions;
            }
            final int[] entries = exceptionsAttribute.getCheckedExceptionsCPI();
            checkedExceptions = new Klass[entries.length];
            for (int i = 0; i < entries.length; ++i) {
                checkedExceptions[i] = getConstantPool().classAt(entries[i]).resolve(getConstantPool(), entries[i]);
            }
        }
        return checkedExceptions;
    }

    public Klass getReturnType() {
        if (returnType == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            returnType = getContext().getRegistries().resolve(getSignature().getReturnTypeDescriptor(), getClassLoader());
        }
        return returnType;
    }

    public int getParameterCount() {
        return getParameterTypes().length;
    }

    public StaticObject getClassLoader() {
        ConstantPool pool = getConstantPool();
        if (pool == null) {
            return null;
        }
        return pool.getClassLoader();
    }

    public boolean isIntrinsified() {
        return intrinsified;
    }

    public static class Builder implements BuilderBase<MethodInfo> {
        private ObjectKlass declaringClass;
        private String name;
        private SignatureDescriptor signature;
        private byte[] code;
        private int maxStackSize;
        private int maxLocals;
        private int modifiers;
        private ExceptionHandler[] exceptionHandlers;
        private LineNumberTable lineNumberTable;
        private LocalVariableTable localVariableTable;
        private ExceptionsAttribute exceptions;

        public Builder setDeclaringClass(ObjectKlass declaringClass) {
            this.declaringClass = declaringClass;
            return this;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setSignature(SignatureDescriptor signature) {
            this.signature = signature;
            return this;
        }

        public Builder setCode(byte[] code) {
            this.code = code;
            return this;
        }

        public Builder setMaxStackSize(int maxStackSize) {
            this.maxStackSize = maxStackSize;
            return this;
        }

        public Builder setMaxLocals(int maxLocals) {
            this.maxLocals = maxLocals;
            return this;
        }

        public Builder setModifiers(int modifiers) {
            this.modifiers = modifiers;
            return this;
        }

        public Builder setExceptionHandlers(ExceptionHandler[] exceptionHandlers) {
            this.exceptionHandlers = exceptionHandlers;
            return this;
        }

        public Builder setLineNumberTable(LineNumberTable lineNumberTable) {
            this.lineNumberTable = lineNumberTable;
            return this;
        }

        public Builder setLocalVariableTable(LocalVariableTable localVariableTable) {
            this.localVariableTable = localVariableTable;
            return this;
        }

        @Override
        public MethodInfo build() {
            return MethodInfo.create(declaringClass, name, signature, code, maxStackSize, maxLocals, modifiers, exceptionHandlers, lineNumberTable, localVariableTable, exceptions);
        }

        public Builder setCheckedExceptions(ExceptionsAttribute exceptions) {
            this.exceptions = exceptions;
            return this;
        }
    }

    private static MethodInfo create(ObjectKlass declaringClass, String name, SignatureDescriptor signature, byte[] code, int maxStackSize, int maxLocals,
                    int modifiers, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable, ExceptionsAttribute exceptions) {
        return new MethodInfo(declaringClass, name, signature, code, maxStackSize, maxLocals, modifiers, exceptionHandlers, lineNumberTable, localVariableTable, exceptions);
    }

    public boolean isFinal() {
        return ModifiersProvider.super.isFinalFlagSet();
    }

    /**
     * Returns the LineNumberTable of this method or null if this method does not have a line
     * numbers table.
     */
    public LineNumberTable getLineNumberTable() {
        return lineNumberTable;
    }

    /**
     * Returns the local variable table of this method or null if this method does not have a local
     * variable table.
     */
    public LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    /**
     * Checks whether the method has bytecodes associated with it. Methods without bytecodes are
     * either abstract or native methods.
     *
     * @return whether the definition of this method is Java bytecodes
     */
    public boolean hasBytecodes() {
        return isConcrete() && !isNative();
    }

    /**
     * Checks whether the method has a receiver parameter - i.e., whether it is not static.
     *
     * @return whether the method has a receiver parameter
     */
    public boolean hasReceiver() {
        return !isStatic();
    }

    /**
     * Determines if this method is {@link java.lang.Object#Object()}.
     */
    public boolean isJavaLangObjectInit() {
        return getDeclaringClass().isJavaLangObject() && getName().equals("<init>");
    }
}
