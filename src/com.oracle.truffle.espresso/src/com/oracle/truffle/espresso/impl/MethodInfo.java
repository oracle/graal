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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.reflect.Modifier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.jni.Mangle;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.LineNumberTable;
import com.oracle.truffle.espresso.meta.LocalVariableTable;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.JNINativeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.types.SignatureDescriptor;
import com.oracle.truffle.nfi.types.NativeSimpleType;

public final class MethodInfo implements ModifiersProvider {

    public final static MethodInfo[] EMPTY_ARRAY = new MethodInfo[0];

    private final Klass declaringClass;
    private final String name;
    private final SignatureDescriptor signature;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final byte[] code;

    private final int maxStackSize;
    private final int maxLocals;
    private final ExceptionHandler[] exceptionHandlers;
    private final LineNumberTable lineNumberTable;
    private final LocalVariableTable localVariableTable;
    private final int modifiers;

    @CompilerDirectives.CompilationFinal private CallTarget callTarget;
    @CompilerDirectives.CompilationFinal private Klass returnType;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private Klass[] parameterTypes;


    MethodInfo(Klass declaringClass, String name, SignatureDescriptor signature,
                    byte[] code, int maxStackSize, int maxLocals, int modifiers,
                    ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable) {
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

    public Klass getDeclaringClass() {
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

    public boolean isVarArgs() {
        throw EspressoError.unimplemented();
    }

    public boolean canBeStaticallyBound() {
        throw EspressoError.unimplemented();
    }

    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlers;
    }

    public StackTraceElement asStackTraceElement(int bci) {
        throw EspressoError.unimplemented();
    }

    public ConstantPool getConstantPool() {
        return declaringClass.getConstantPool();
    }

    public boolean canBeInlined() {
        throw EspressoError.unimplemented();
    }

    public boolean hasNeverInlineDirective() {
        throw EspressoError.unimplemented();
    }

    public boolean shouldBeInlined() {
        throw EspressoError.unimplemented();
    }

    private static NativeSimpleType kindToType(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return NativeSimpleType.UINT8; // ?
            case Short:
                return NativeSimpleType.SINT16;
            case Char:
                return NativeSimpleType.UINT16;
            case Long:
                return NativeSimpleType.SINT64;
            case Float:
                return NativeSimpleType.FLOAT;
            case Double:
                return NativeSimpleType.DOUBLE;
            case Int:
                return NativeSimpleType.SINT32;
            case Byte:
                return NativeSimpleType.SINT8;
            case Void:
                return NativeSimpleType.VOID;
            case Object:
                return NativeSimpleType.OBJECT;
            default:
                throw EspressoError.shouldNotReachHere();
        }
    }

    private static TruffleObject bind(TruffleObject library, Meta.Method m, String mangledName) {
        StringBuilder sb = new StringBuilder("(").append(NativeSimpleType.POINTER); // Prepend
                                                                                    // JNIEnv.
        SignatureDescriptor signature = m.rawMethod().getSignature();
        if (!m.isStatic()) {
            sb.append(", ").append(NativeSimpleType.OBJECT); // this
        }
        int argCount = signature.getParameterCount(false);
        for (int i = 0; i < argCount; ++i) {
            sb.append(", ").append(kindToType(signature.getParameterKind(i)));
        }
        sb.append("):").append(kindToType(signature.resultKind()));
        try {
            TruffleObject fn = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, mangledName);
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), fn, "bind", sb.toString());
        } catch (UnsupportedTypeException | UnsupportedMessageException | UnknownIdentifierException | ArityException e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    @CompilerDirectives.TruffleBoundary
    public CallTarget getCallTarget() {
        // TODO(peterssen): Make lazy call target thread-safe.
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CallTarget redirectedMethod = getContext().getVm().getIntrinsic(this);
            if (redirectedMethod != null) {
                callTarget = redirectedMethod;
            } else {
                if (this.isNative()) {
                    System.err.println("Linking native method: " + meta(this).getDeclaringClass().getName() + "#" + getName() + " " + getSignature());
                    Meta meta = getContext().getMeta();
                    Meta.Method.WithInstance findNative = meta.knownKlass(ClassLoader.class)
                            .staticMethod("findNative", long.class, ClassLoader.class, String.class);

                    // Lookup the short name first, otherwise lookup the long name (with signature).
                    for (boolean withSignature: new boolean[]{false, true}) {
                        String mangledName = Mangle.mangleMethod(meta(this), withSignature);
                        long handle = (long) findNative.invoke(getDeclaringClass().getClassLoader(), mangledName);
                        if (handle == 0) { // not found
                            continue ;
                        }
                        TruffleObject library = getContext().getNativeLibraries().get(handle);
                        TruffleObject nativeMethod = bind(library, meta(this), mangledName);
                        callTarget = Truffle.getRuntime().createCallTarget(new JNINativeNode(getContext().getLanguage(), nativeMethod));
                        break;
                    }

                    if (callTarget == null) {
                        throw meta.throwEx(UnsatisfiedLinkError.class);
                    }
                } else {
                    callTarget = Truffle.getRuntime().createCallTarget(new EspressoRootNode(getContext().getLanguage(), this, getContext().getVm()));
                }
            }
        }

        return callTarget;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isConstructor() {
        assert signature.resultKind() == JavaKind.Void;
        assert isStatic();
        assert signature.getParameterCount(false) == 0;
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

    public Object getClassLoader() {
        ConstantPool pool = getConstantPool();
        if (pool == null) {
            return null;
        }
        return pool.getClassLoader();
    }

    public static class Builder implements BuilderBase<MethodInfo> {
        private Klass declaringClass;
        private String name;
        private SignatureDescriptor signature;
        private byte[] code;
        private int maxStackSize;
        private int maxLocals;
        private int modifiers;
        private ExceptionHandler[] exceptionHandlers;
        private LineNumberTable lineNumberTable;
        private LocalVariableTable localVariableTable;

        public Builder setDeclaringClass(Klass declaringClass) {
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
            return MethodInfo.create(declaringClass, name, signature, code, maxStackSize, maxLocals, modifiers, exceptionHandlers, lineNumberTable, localVariableTable);
        }
    }

    private static MethodInfo create(Klass declaringClass, String name, SignatureDescriptor signature, byte[] code, int maxStackSize, int maxLocals,
                    int modifiers, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable) {
        return new MethodInfo(declaringClass, name, signature, code, maxStackSize, maxLocals, modifiers, exceptionHandlers, lineNumberTable, localVariableTable);
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
