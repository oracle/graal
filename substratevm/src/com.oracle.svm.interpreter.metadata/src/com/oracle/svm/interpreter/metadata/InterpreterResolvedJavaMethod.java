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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.interpreter.metadata.Bytecodes.BREAKPOINT;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.Constants;
import com.oracle.svm.espresso.classfile.ParserMethod;
import com.oracle.svm.espresso.classfile.attributes.CodeAttribute;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.shared.vtable.PartialMethod;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Encapsulates resolved methods used under close-world assumptions, compiled and interpretable, but
 * also abstract methods for vtable calls.
 */
public final class InterpreterResolvedJavaMethod implements ResolvedJavaMethod, CremaMethodAccess {

    public static final LocalVariableTable EMPTY_LOCAL_VARIABLE_TABLE = new LocalVariableTable(new Local[0]);

    public static final int UNKNOWN_METHOD_ID = 0;

    private final Symbol<Signature> signatureSymbol;

    // Should be final (not its contents, it can be patched with BREAKPOINT).
    // These are the bytecodes executed by the interpreter e.g. can be patched with BREAKPOINT.
    private byte[] interpretedCode;
    private final Symbol<Name> name;
    private final int maxLocals;
    private final int maxStackSize;
    private final int modifiers;

    @Platforms(Platform.HOSTED_ONLY.class) //
    private ResolvedJavaMethod originalMethod;

    private final InterpreterResolvedObjectType declaringClass;
    private final InterpreterUnresolvedSignature signature;

    private final LineNumberTable lineNumberTable;

    private ExceptionHandler[] exceptionHandlers;

    private LocalVariableTable localVariableTable;

    private ReferenceConstant<FunctionPointerHolder> nativeEntryPoint;

    // Token set by the toggle of method enter/exit events.
    private volatile Object interpreterExecToken;

    public static class InlinedBy {
        public InterpreterResolvedJavaMethod holder;
        public Set<InterpreterResolvedJavaMethod> inliners;

        public InlinedBy(InterpreterResolvedJavaMethod holder, Set<InterpreterResolvedJavaMethod> inliners) {
            this.holder = holder;
            this.inliners = inliners;
        }
    }

    protected InlinedBy inlinedBy;

    public static final int VTBL_NO_ENTRY = -1;
    public static final int VTBL_ONE_IMPL = -2;
    private int vtableIndex = VTBL_NO_ENTRY;
    private InterpreterResolvedJavaMethod oneImplementation;

    /* slot in GOT */
    private int gotOffset;

    public static final int EST_NO_ENTRY = -5;
    /* slot in EST (EnterStubTable) */
    private int enterStubOffset = EST_NO_ENTRY;

    /**
     * Unique identifier for methods in compiled frames.
     * <p>
     * Only valid if != 0, 0 means unknown. Allows to precisely extract the
     * {@link InterpreterResolvedJavaMethod interpreter method instance} from a compiled frame.
     */
    private int methodId;

    @Platforms(Platform.HOSTED_ONLY.class) public boolean needMethodBody;

    // Only called during universe building
    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterResolvedJavaMethod(ResolvedJavaMethod originalMethod, Symbol<Name> name, int maxLocals, int maxStackSize, int modifiers, InterpreterResolvedObjectType declaringClass,
                    InterpreterUnresolvedSignature signature,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        this(name, maxLocals, maxStackSize, modifiers, declaringClass, signature, code, exceptionHandlers, lineNumberTable, localVariableTable, nativeEntryPoint, vtableIndex, gotOffset,
                        enterStubOffset, methodId);
        this.originalMethod = originalMethod;
        this.needMethodBody = false;
        this.inlinedBy = new InterpreterResolvedJavaMethod.InlinedBy(this, new HashSet<>());
    }

    private InterpreterResolvedJavaMethod(Symbol<Name> name,
                    int maxLocals, int maxStackSize,
                    int modifiers,
                    InterpreterResolvedObjectType declaringClass, InterpreterUnresolvedSignature signature,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        this.name = name;
        this.maxLocals = maxLocals;
        this.maxStackSize = maxStackSize;
        this.modifiers = modifiers;
        this.declaringClass = declaringClass;
        this.signature = signature;
        this.interpretedCode = code;
        this.exceptionHandlers = exceptionHandlers;
        this.lineNumberTable = lineNumberTable;
        this.localVariableTable = localVariableTable;

        this.nativeEntryPoint = nativeEntryPoint;
        this.vtableIndex = vtableIndex;
        this.gotOffset = gotOffset;
        this.enterStubOffset = enterStubOffset;
        this.methodId = methodId;
        this.inlinedBy = new InlinedBy(this, new HashSet<>());

        this.signatureSymbol = CremaMethodAccess.toSymbol(signature, SymbolsSupport.getSignatures());
    }

    private InterpreterResolvedJavaMethod(InterpreterResolvedObjectType declaringClass, ParserMethod m, int vtableIndex) {
        assert RuntimeClassLoading.isSupported();
        this.name = m.getName();
        this.signatureSymbol = m.getSignature();

        this.declaringClass = declaringClass;
        this.modifiers = m.getFlags() & Constants.JVM_RECOGNIZED_METHOD_MODIFIERS;
        CodeAttribute codeAttribute = (CodeAttribute) m.getAttribute(CodeAttribute.NAME);
        if (codeAttribute != null) {
            this.maxLocals = codeAttribute.getMaxLocals();
            this.maxStackSize = codeAttribute.getMaxStack();
            this.interpretedCode = codeAttribute.getOriginalCode();
            this.lineNumberTable = CremaMethodAccess.toJVMCI(codeAttribute.getLineNumberTableAttribute());
        } else {
            this.maxLocals = 0;
            this.maxStackSize = 0;
            this.interpretedCode = null;
            this.lineNumberTable = null;
        }
        this.signature = CremaMethodAccess.toJVMCI(m.getSignature(), SymbolsSupport.getTypes());

        this.vtableIndex = vtableIndex;
        this.nativeEntryPoint = null;

        this.gotOffset = -2 /* -GOT_NO_ENTRY */;
        this.enterStubOffset = EST_NO_ENTRY;
        this.methodId = UNKNOWN_METHOD_ID;
        this.inlinedBy = new InlinedBy(this, new HashSet<>());

    }

    public static InterpreterResolvedJavaMethod create(InterpreterResolvedObjectType declaringClass, ParserMethod m, int vtableIndex) {
        return new InterpreterResolvedJavaMethod(declaringClass, m, vtableIndex);
    }

    @VisibleForSerialization
    public static InterpreterResolvedJavaMethod create(String name, int maxLocals, int maxStackSize, int modifiers, InterpreterResolvedObjectType declaringClass,
                    InterpreterUnresolvedSignature signature,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        return new InterpreterResolvedJavaMethod(nameSymbol, maxLocals, maxStackSize, modifiers, declaringClass, signature, code,
                        exceptionHandlers, lineNumberTable, localVariableTable, nativeEntryPoint, vtableIndex, gotOffset, enterStubOffset, methodId);
    }

    // Only called during universe building
    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedJavaMethod create(ResolvedJavaMethod originalMethod, String name, int maxLocals, int maxStackSize, int modifiers,
                    InterpreterResolvedObjectType declaringClass,
                    InterpreterUnresolvedSignature signature,
                    byte[] code, ExceptionHandler[] exceptionHandlers, LineNumberTable lineNumberTable, LocalVariableTable localVariableTable,
                    ReferenceConstant<FunctionPointerHolder> nativeEntryPoint, int vtableIndex, int gotOffset, int enterStubOffset, int methodId) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        return new InterpreterResolvedJavaMethod(originalMethod, nameSymbol, maxLocals, maxStackSize, modifiers, declaringClass, signature, code,
                        exceptionHandlers, lineNumberTable, localVariableTable, nativeEntryPoint, vtableIndex, gotOffset, enterStubOffset, methodId);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean needsMethodBody() {
        return needMethodBody;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ResolvedJavaMethod getOriginalMethod() {
        return originalMethod;
    }

    /**
     * Returns the bytecodes executed by the interpreter, may include BREAKPOINT and other
     * non-standard bytecodes used by the interpreter. For a spec-compliant, without BREAKPOINT and
     * non-standard bytecodes use {@link #getCode()}
     */
    public byte[] getInterpretedCode() {
        return interpretedCode;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setCode(byte[] code) {
        VMError.guarantee(originalCode == null);
        this.interpretedCode = code;
    }

    private volatile byte[] originalCode;

    public int getOriginalOpcodeAt(int bci) {
        return getCode()[bci] & 0xFF;
    }

    @Override
    public byte[] getCode() {
        if (interpretedCode == null) {
            return null;
        }
        byte[] result = originalCode;
        if (result == null) {
            synchronized (this) {
                result = originalCode;
                if (result == null) {
                    originalCode = result = getInterpretedCode().clone();
                    verifySanitizedCode(result); // assert
                }
            }
        }
        return result;
    }

    private static void verifySanitizedCode(byte[] code) {
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            int currentBC = BytecodeStream.currentBC(code, bci);
            VMError.guarantee(Bytecodes.BREAKPOINT != currentBC);
        }
    }

    @Override
    public int getCodeSize() {
        if (interpretedCode == null) {
            return 0;
        }
        return interpretedCode.length;
    }

    @Override
    public Symbol<Name> getSymbolicName() {
        return name;
    }

    @Override
    public Symbol<Signature> getSymbolicSignature() {
        return signatureSymbol;
    }

    @Override
    public String getName() {
        return name.toString();
    }

    @Override
    public InterpreterResolvedObjectType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public InterpreterUnresolvedSignature getSignature() {
        return signature;
    }

    @Override
    public int getMaxLocals() {
        return maxLocals;
    }

    @Override
    public int getMaxStackSize() {
        return maxStackSize;
    }

    @Override
    public boolean isDeclared() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isClassInitializer() {
        return "<clinit>".equals(getName()) && isStatic();
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(getName()) && !isStatic();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        ExceptionHandler[] result = exceptionHandlers;
        VMError.guarantee(result != null);
        return result;
    }

    @Override
    public InterpreterConstantPool getConstantPool() {
        return declaringClass.getConstantPool();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return lineNumberTable;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public String toString() {
        return "InterpreterResolvedJavaMethod<holder=" + getDeclaringClass().getName() + " name=" + getName() + " descriptor=" + getSignature().toMethodDescriptor() + ">";
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setExceptionHandlers(ExceptionHandler[] exceptionHandlers) {
        this.exceptionHandlers = MetadataUtil.requireNonNull(exceptionHandlers);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setLocalVariableTable(LocalVariableTable localVariableTable) {
        this.localVariableTable = MetadataUtil.requireNonNull(localVariableTable);
    }

    private void patchOpcode(int bci, int newOpcode) {
        BytecodeStream.patchOpcodeOpaque(interpretedCode, bci, newOpcode);
    }

    public void ensureCanSetBreakpointAt(int bci) {
        if (!hasBytecodes()) {
            throw new IllegalArgumentException("Cannot set breakpoint: method " + name + " doesn't have bytecodes");
        }
        if (bci < 0 || getCodeSize() <= bci) {
            throw new IllegalArgumentException("Cannot set breakpoint: BCI out of bounds");
        }
        if (!isValidBCI(getCode(), bci)) {
            throw new IllegalArgumentException("Cannot set breakpoint: targetBCI is not a valid first opcode");
        }
    }

    public void toggleBreakpoint(int bci, boolean enabled) {
        ensureCanSetBreakpointAt(bci);
        if (enabled) {
            patchOpcode(bci, BREAKPOINT);
        } else {
            int originalOpcode = getOriginalOpcodeAt(bci);
            patchOpcode(bci, originalOpcode);
        }
    }

    public static boolean isValidBCI(byte[] code, int targetBCI) {
        for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
            if (bci == targetBCI) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unique identifier for methods in compiled frames.
     * <p>
     * Only valid if != 0, 0 means unknown. Allows to precisely extract the
     * {@link InterpreterResolvedJavaMethod interpreter method instance} from a compiled frame.
     */
    public int getMethodId() {
        return methodId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMethodId(int methodId) {
        assert methodId >= 0;
        this.methodId = methodId;
    }

    public void setGOTOffset(int gotOffset) {
        this.gotOffset = gotOffset;
    }

    public int getGotOffset() {
        return gotOffset;
    }

    public void setEnterStubOffset(int offset) {
        this.enterStubOffset = offset;
    }

    public int getEnterStubOffset() {
        return enterStubOffset;
    }

    public boolean hasNativeEntryPoint() {
        return nativeEntryPoint != null;
    }

    public MethodPointer getNativeEntryPoint() {
        if (nativeEntryPoint == null) {
            return Word.nullPointer();
        }
        return (MethodPointer) nativeEntryPoint.getReferent().functionPointer;
    }

    public ReferenceConstant<FunctionPointerHolder> getNativeEntryPointHolderConstant() {
        return nativeEntryPoint;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setNativeEntryPoint(MethodPointer nativeEntryPoint) {
        if (this.nativeEntryPoint != null && nativeEntryPoint != null) {
            /* already set, verify if it's the same */
            ResolvedJavaMethod setMethod = ((MethodPointer) this.nativeEntryPoint.getReferent().functionPointer).getMethod();
            VMError.guarantee(setMethod.equals(nativeEntryPoint.getMethod()));
            return;
        }

        if (nativeEntryPoint == null) {
            this.nativeEntryPoint = null;
        } else {
            this.nativeEntryPoint = ReferenceConstant.createFromNonNullReference(new FunctionPointerHolder(nativeEntryPoint));
        }
    }

    public int getVTableIndex() {
        return vtableIndex;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setVTableIndex(int vtableIndex) {
        VMError.guarantee(vtableIndex == VTBL_NO_ENTRY || (!isStatic() && !isConstructor()));
        if (vtableIndex >= 0) {
            VMError.guarantee(!isFinal());
        }
        this.vtableIndex = vtableIndex;
    }

    @Override
    public boolean hasVTableIndex() {
        return vtableIndex != VTBL_NO_ENTRY && vtableIndex != VTBL_ONE_IMPL;
    }

    public void setOneImplementation(InterpreterResolvedJavaMethod oneImplementation) {
        this.oneImplementation = oneImplementation;
    }

    public InterpreterResolvedJavaMethod getOneImplementation() {
        /* if VTBL_ONE_IMPL is set, oneImplementation must have an assignment */
        VMError.guarantee(vtableIndex != VTBL_ONE_IMPL || oneImplementation != null);
        return oneImplementation;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterpreterResolvedJavaMethod)) {
            return false;
        }
        InterpreterResolvedJavaMethod that = (InterpreterResolvedJavaMethod) other;
        return name.equals(that.name) && declaringClass.equals(that.declaringClass) && signature.equals(that.signature);
    }

    @Override
    public int hashCode() {
        int result = MetadataUtil.hashCode(name);
        result = 31 * result + MetadataUtil.hashCode(declaringClass);
        result = 31 * result + MetadataUtil.hashCode(signature);
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isInterpreterExecutable() {
        return hasBytecodes();
    }

    public Set<InterpreterResolvedJavaMethod> getInlinedBy() {
        return inlinedBy.inliners;
    }

    public void addInliner(InterpreterResolvedJavaMethod inliner) {
        inlinedBy.inliners.add(inliner);
    }

    public Object getInterpreterExecToken() {
        return interpreterExecToken;
    }

    public void setInterpreterExecToken(Object interpreterExecToken) {
        this.interpreterExecToken = interpreterExecToken;
    }

    @Override
    public InterpreterResolvedJavaMethod asMethodAccess() {
        return this;
    }

    @Override
    public PartialMethod<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> withVTableIndex(int index) {
        assert vtableIndex == VTBL_NO_ENTRY;
        vtableIndex = index;
        return this;
    }

    // region Unimplemented methods

    @Override
    public boolean shouldSkipLoadingConstraints() {
        throw VMError.unimplemented("shouldSkipLoadingConstraints");
    }

    @Override
    public CodeAttribute getCodeAttribute() {
        throw VMError.unimplemented("getCodeAttribute");
    }

    @Override
    public boolean accessChecks(InterpreterResolvedJavaType accessingClass, InterpreterResolvedJavaType holderClass) {
        throw VMError.unimplemented("accessChecks");
    }

    @Override
    public void loadingConstraints(InterpreterResolvedJavaType accessingClass, Function<String, RuntimeException> errorHandler) {
        throw VMError.unimplemented("loadingConstraints");
    }

    @Override
    public com.oracle.svm.espresso.classfile.ExceptionHandler[] getSymbolicExceptionHandlers() {
        throw VMError.unimplemented("getSymbolicExceptionHandlers");
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean canBeInlined() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean shouldBeInlined() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Constant getEncoding() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Annotation[] getAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isSynthetic() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isVarArgs() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isBridge() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isDefault() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean canBeStaticallyBound() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public void reprofile() {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods
}
