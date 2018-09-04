package com.oracle.truffle.espresso.impl;

import java.lang.reflect.Modifier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.LineNumberTable;
import com.oracle.truffle.espresso.meta.LocalVariableTable;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.types.SignatureDescriptor;

public final class MethodInfo implements ModifiersProvider {

    public final static MethodInfo[] EMPTY_ARRAY = new MethodInfo[0];

    private final Klass declaringClass;
    private final String name;
    private final SignatureDescriptor signature;

    // @CompilationFinal(dimensions = 1)
    private final byte[] code;

    private final int maxStackSize;
    private final int maxLocals;
    private final ExceptionHandler[] exceptionHandlers;
    private final LineNumberTable lineNumberTable;
    private final LocalVariableTable localVariableTable;
    private final int modifiers;

    @CompilerDirectives.CompilationFinal private CallTarget callTarget;

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

    public boolean isClassInitializer() {
        assert signature.resultKind() == JavaKind.Void;
        assert isStatic();
        assert signature.getParameterCount(false) == 0;
        return "<clinit>".equals(getName());
    }

    public boolean isConstructor() {
        assert signature.resultKind() == JavaKind.Void;
        assert isStatic();
        assert signature.getParameterCount(false) == 0;
        return "<init>".equals(getName());
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

    @CompilerDirectives.TruffleBoundary
    public CallTarget getCallTarget() {
        // TODO(peterssen): Make lazy call target thread-safe.
        if (callTarget == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CallTarget redirectedMethod = getContext().getVm().getIntrinsic(this);
            if (redirectedMethod != null) {
                callTarget = redirectedMethod;
            } else {
                callTarget = Truffle.getRuntime().createCallTarget(new EspressoRootNode(getContext().getLanguage(), this, getContext().getVm()));
            }
        }
        return callTarget;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    @CompilerDirectives.CompilationFinal(dimensions = 1)
    private Klass[] parameterTypes;

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
